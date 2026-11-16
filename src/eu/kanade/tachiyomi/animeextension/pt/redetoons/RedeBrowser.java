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
 * Opens a watch page in an off-screen WebView, logging in with the saved account
 * when the site asks, and waits until the site's player has loaded a video. By
 * then the site has accepted the session and its security check, so the
 * extension can call the play API itself. The cookies land in the shared WebView
 * cookie store, which the app's HTTP client also uses.
 */
final class RedeBrowser {

    enum Result { OK, NEEDS_LOGIN, LOGIN_REJECTED, TIMEOUT }

    private static final long TIMEOUT_MS = 60_000;
    private static final long POLL_MS = 1_000;
    private static final int MAX_RELOADS = 2;

    /** Video files and HLS/DASH manifests, including extensionless /playlist/ endpoints. */
    static final Pattern MEDIA = Pattern.compile(
            "(?i)^https?://[^?#]+(\\.(mp4|m3u8|mpd)|/playlist/[^/?#.]+)([?#].*)?$");

    /**
     * The site's anti-devtools script redirects the whole page to YouTube when it
     * thinks a debugger is attached, which happens in an embedded WebView.
     */
    private static final Pattern GUARD_SCRIPT = Pattern.compile(
            "(?i)/(su-guard|gw|disable-devtool)[^/]*\\.js([?#].*)?$");

    /** Page state, and whether the player in any frame got a video or gave up. */
    private static final String STATE_JS = "(function(){var t=document.querySelector('[name=cf-turnstile-response]');"
            + "var e=document.querySelector('.form-error,.error,.alert-danger,[role=alert]');var v=false,x=false;"
            + "function scan(w){if(!w)return;try{var d=w.document;var l=d.querySelectorAll('video');"
            + "for(var i=0;i<l.length;i++){if(l[i].currentSrc||l[i].getAttribute('src'))v=true}"
            + "if(d.body&&d.body.classList.contains('bp-has-error'))x=true;"
            + "for(var k=0;k<w.frames.length;k++)scan(w.frames[k])}catch(y){}}scan(window);"
            + "return JSON.stringify({h:location.hostname,p:location.pathname,t:document.title||'',"
            + "u:!!document.getElementById('username'),c:t?t.value.length:-1,"
            + "e:e?(e.textContent||'').trim():'',v:v,x:x})})()";

    private final Context context;
    private final String userAgent;
    private final String username;
    private final String password;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile Result result = Result.TIMEOUT;
    private WebView webView;
    private String startUrl;
    private String siteHost;
    private int reloads;
    private boolean filled;
    private long submittedAt;

    private RedeBrowser(Context context, String userAgent, String username, String password) {
        this.context = context;
        this.userAgent = userAgent;
        this.username = username;
        this.password = password;
    }

    /** Blocks the calling (background) thread until the player loaded or the attempt fails. */
    static synchronized Result unlock(Context context, String watchUrl, String userAgent,
                                      String username, String password) {
        if (Looper.myLooper() == Looper.getMainLooper()) return Result.TIMEOUT;
        RedeBrowser browser = new RedeBrowser(context, userAgent, username, password);
        return browser.run(watchUrl);
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
                if (GUARD_SCRIPT.matcher(request.getUrl().toString()).find()) {
                    return new WebResourceResponse("application/javascript", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
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

        // Pushed off the site (anti-devtools redirect): load the watch page again.
        if (!host.isEmpty() && !host.equals(siteHost) && !host.endsWith("cloudflare.com")) {
            if (reloads++ < MAX_RELOADS) webView.loadUrl(startUrl);
            return;
        }

        if (path.contains("/watch/") && !title.contains("Verifica")) {
            // The player got its video (or reported an error): the session is ready.
            if (state.optBoolean("v") || state.optBoolean("x")) finish(Result.OK);
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
