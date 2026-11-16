package eu.kanade.tachiyomi.animeextension.pt.redetoons;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Opens a watch page in an off-screen WebView so the site's Cloudflare check can
 * issue the play ticket, logging in with the saved account when the site asks.
 * The cookies land in the shared WebView cookie store, which the app's HTTP
 * client also uses.
 */
final class RedeBrowser {

    enum Result { OK, NEEDS_LOGIN, LOGIN_REJECTED, TIMEOUT }

    private static final long TIMEOUT_MS = 60_000;
    private static final long POLL_MS = 1_000;
    private static final int MAX_RELOADS = 2;

    /**
     * Video files and HLS/DASH manifests requested by the site's player, including
     * extensionless playlist endpoints such as /playlist/123?token=...
     */
    static final Pattern MEDIA = Pattern.compile(
            "(?i)^https?://[^?#]+(\\.(mp4|m3u8|mpd)|/playlist/[^/?#.]+)([?#].*)?$");

    /** Hosts the player never takes its video from. */
    private static final Pattern IGNORED_HOST = Pattern.compile(
            "(?i)(^|\\.)(youtube\\.com|youtu\\.be|googlevideo\\.com|ytimg\\.com|google\\.com|"
                    + "cloudflareinsights\\.com|amung\\.us|waust\\.at)$");

    /**
     * The site's anti-devtools script redirects the whole page to YouTube when it
     * thinks a debugger is attached, which happens in an embedded WebView.
     */
    private static final Pattern GUARD_SCRIPT = Pattern.compile(
            "(?i)/(su-guard|gw|disable-devtool)[^/]*\\.js([?#].*)?$");

    /** Starts the player inside the same-origin iframes, as a tap on play would. */
    private static final String PLAY_JS = "(function(){function go(d,top){if(!d)return;"
            + "var v=d.querySelectorAll('video');for(var i=0;i<v.length;i++){try{v[i].muted=true;"
            + "if(v[i].paused){var p=v[i].play();if(p&&p.catch)p.catch(function(){})}}catch(e){}}"
            + "if(!top){var b=d.querySelectorAll('button,[role=button],.vjs-big-play-button,.jw-display-icon-container,"
            + ".plyr__control--overlaid,[class*=big-play],[class*=play-button],[class*=btn-play],[aria-label*=lay],[aria-label*=eproduz]');"
            + "var n=0;for(var j=0;j<b.length&&n<3;j++){var c=String(b[j].className||'')+' '+(b[j].getAttribute('aria-label')||'');"
            + "if(/play|reproduz|assistir/i.test(c)&&!/pause|playlist|playback|speed|rate/i.test(c)){try{b[j].click();n++}catch(e){}}}}"
            + "var f=d.querySelectorAll('iframe');for(var k=0;k<f.length;k++){try{go(f[k].contentDocument,false)}catch(e){}}}"
            + "go(document,true)})()";

    /** Page state, plus any video address the player already loaded. */
    private static final String STATE_JS = "(function(){var t=document.querySelector('[name=cf-turnstile-response]');"
            + "var e=document.querySelector('.form-error,.error,.alert-danger,[role=alert]');"
            + "var re=/^https?:\\/\\/[^?#]+(\\.(mp4|m3u8|mpd)|\\/playlist\\/[^\\/?#.]+)([?#].*)?$/i;"
            + "var bad=/(^|\\.)(youtube\\.com|youtu\\.be|googlevideo\\.com|ytimg\\.com)$/i;var m='';"
            + "function ok(u){if(!u||!re.test(u))return false;try{return !bad.test(new URL(u).hostname)}catch(x){return false}}"
            + "function scan(w){if(m||!w)return;try{var d=w.document;var v=d.querySelectorAll('video,video source');"
            + "for(var i=0;i<v.length&&!m;i++){var s=v[i].currentSrc||v[i].src||'';if(ok(s))m=s}"
            + "var r=w.performance&&w.performance.getEntriesByType?w.performance.getEntriesByType('resource'):[];"
            + "for(var j=0;j<r.length&&!m;j++){if(ok(r[j].name))m=r[j].name}"
            + "for(var k=0;k<w.frames.length&&!m;k++)scan(w.frames[k])}catch(x){}}scan(window);"
            + "return JSON.stringify({h:location.hostname,p:location.pathname,t:document.title||'',"
            + "u:!!document.getElementById('username'),c:t?t.value.length:-1,"
            + "e:e?(e.textContent||'').trim():'',m:m})})()";

    private final Context context;
    private final String userAgent;
    private final String username;
    private final String password;
    private final boolean captureMedia;
    private volatile String mediaUrl;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile Result result = Result.TIMEOUT;
    private WebView webView;
    private String startUrl;
    private String siteHost;
    private int reloads;
    private boolean filled;
    private long submittedAt;

    private RedeBrowser(Context context, String userAgent, String username, String password,
                        boolean captureMedia) {
        this.captureMedia = captureMedia;
        this.context = context;
        this.userAgent = userAgent;
        this.username = username;
        this.password = password;
    }

    /** Blocks the calling (background) thread until the page is unlocked or the attempt fails. */
    static synchronized Result unlock(Context context, String watchUrl, String userAgent,
                                      String username, String password) {
        if (Looper.myLooper() == Looper.getMainLooper()) return Result.TIMEOUT;
        RedeBrowser browser = new RedeBrowser(context, userAgent, username, password, false);
        return browser.run(watchUrl);
    }

    /** Video address the site's player requested, after login and the security check. */
    static final class Capture {
        final Result result;
        final String url;

        Capture(Result result, String url) {
            this.result = result;
            this.url = url;
        }
    }

    /** Opens the watch page and returns the first video file its player requests. */
    static synchronized Capture captureVideo(Context context, String watchUrl, String userAgent,
                                             String username, String password) {
        if (Looper.myLooper() == Looper.getMainLooper()) return new Capture(Result.TIMEOUT, null);
        RedeBrowser browser = new RedeBrowser(context, userAgent, username, password, true);
        Result result = browser.run(watchUrl);
        return new Capture(result, browser.mediaUrl);
    }

    /** Cookies the WebView holds for a URL, so the app's player can send them too. */
    static String cookiesFor(String url) {
        try {
            return CookieManager.getInstance().getCookie(url);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Result run(final String url) {
        main.post(new Runnable() {
            @Override
            public void run() {
                start(url);
            }
        });
        try {
            done.await(TIMEOUT_MS + 5_000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.stopLoading();
                    webView.destroy();
                    webView = null;
                }
                CookieManager.getInstance().flush();
            }
        });
        return result;
    }

    private static String hostOf(String url) {
        try {
            return new java.net.URL(url).getHost();
        } catch (java.net.MalformedURLException e) {
            return "";
        }
    }

    private boolean isMedia(String url) {
        return MEDIA.matcher(url).matches() && !IGNORED_HOST.matcher(hostOf(url)).find();
    }

    private void start(String url) {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        startUrl = url;
        siteHost = hostOf(url);
        webView = new WebView(context);
        webView.layout(0, 0, 1080, 1920);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        if (userAgent != null && !userAgent.isEmpty()) webView.getSettings().setUserAgentString(userAgent);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Stay on the site: the anti-devtools redirect and external links lead nowhere useful.
                String host = request.getUrl().getHost();
                return host == null || !(host.equals(siteHost) || host.endsWith("challenges.cloudflare.com"));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (GUARD_SCRIPT.matcher(url).find()) {
                    return new WebResourceResponse("application/javascript", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                if (captureMedia && mediaUrl == null && isMedia(url)) {
                    mediaUrl = url;
                    finish(Result.OK);
                }
                return null;
            }
        });
        webView.loadUrl(url);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                poll(deadline);
            }
        }, POLL_MS);
    }

    private void finish(Result value) {
        result = value;
        done.countDown();
    }

    private void poll(final long deadline) {
        if (webView == null || done.getCount() == 0) return;
        if (System.currentTimeMillis() > deadline) {
            finish(Result.TIMEOUT);
            return;
        }
        webView.evaluateJavascript(STATE_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                try {
                    handle(new JSONObject(new JSONArray("[" + value + "]").getString(0)));
                } catch (Exception ignored) {
                    // Page still loading.
                }
                if (done.getCount() > 0) {
                    main.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            poll(deadline);
                        }
                    }, POLL_MS);
                }
            }
        });
    }

    private void handle(JSONObject state) {
        String host = state.optString("h");
        String path = state.optString("p");
        String title = state.optString("t");
        boolean verifying = title.contains("Verifica");

        // Pushed off the site (anti-devtools redirect): load the watch page again.
        if (!host.isEmpty() && !host.equals(siteHost) && !host.endsWith("cloudflare.com")) {
            if (reloads++ < MAX_RELOADS) webView.loadUrl(startUrl);
            return;
        }

        String found = state.optString("m");
        if (captureMedia && mediaUrl == null && !found.isEmpty() && isMedia(found)) {
            mediaUrl = found;
            finish(Result.OK);
            return;
        }

        if (path.contains("/watch/") && !verifying && !title.isEmpty()) {
            if (!captureMedia) {
                finish(Result.OK);
            } else if (mediaUrl == null) {
                webView.evaluateJavascript(PLAY_JS, null);
            }
            return;
        }
        if (!state.optBoolean("u")) return;

        // Login form.
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            finish(Result.NEEDS_LOGIN);
            return;
        }
        if (!filled) {
            webView.evaluateJavascript(fillScript(), null);
            filled = true;
            return;
        }
        if (submittedAt == 0) {
            // Wait for the Turnstile token when the form has one.
            if (state.optInt("c", -1) == 0) return;
            webView.evaluateJavascript(SUBMIT_JS, null);
            submittedAt = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - submittedAt > 8_000 && !state.optString("e").isEmpty()) {
            finish(Result.LOGIN_REJECTED);
        }
    }

    private String fillScript() {
        return "(function(){var u=document.getElementById('username'),p=document.getElementById('password');"
                + "if(!u||!p)return;var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                + "function put(e,v){e.focus();s.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));}"
                + "put(u," + JSONObject.quote(username) + ");put(p," + JSONObject.quote(password) + ");"
                + "var r=u.form&&u.form.querySelector('input[type=checkbox]');if(r&&!r.checked)r.click();})()";
    }

    private static final String SUBMIT_JS = "(function(){var u=document.getElementById('username');if(!u)return;"
            + "var f=u.form;var b=f&&f.querySelector('button[type=submit],button:not([type])');"
            + "if(b)b.click();else if(f&&f.requestSubmit)f.requestSubmit();})()";
}
