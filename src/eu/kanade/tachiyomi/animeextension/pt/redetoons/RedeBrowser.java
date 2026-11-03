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

    /** Video files requested by the site's player. */
    private static final Pattern MEDIA = Pattern.compile("(?i)^https?://[^?#]+\\.(mp4|m3u8)(\\?.*)?$");

    /** Starts the player inside the same-origin iframe, as a click on play would. */
    private static final String PLAY_JS = "(function(){function go(d){if(!d)return;"
            + "var v=d.querySelectorAll('video');for(var i=0;i<v.length;i++){try{v[i].muted=true;var p=v[i].play();if(p&&p.catch)p.catch(function(){})}catch(e){}}"
            + "var b=d.querySelectorAll('[class*=play],[aria-label*=lay],[aria-label*=eproduz],[title*=lay]');"
            + "for(var j=0;j<b.length&&j<3;j++){try{b[j].click()}catch(e){}}"
            + "var f=d.querySelectorAll('iframe');for(var k=0;k<f.length;k++){try{go(f[k].contentDocument)}catch(e){}}}"
            + "go(document)})()";

    private static final String STATE_JS = "(function(){var t=document.querySelector('[name=cf-turnstile-response]');"
            + "var e=document.querySelector('.form-error,.error,.alert-danger,[role=alert]');"
            + "return JSON.stringify({p:location.pathname,t:document.title||'',"
            + "u:!!document.getElementById('username'),c:t?t.value.length:-1,"
            + "e:e?(e.textContent||'').trim():''})})()";

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

    private void start(String url) {
        final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
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
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (captureMedia && mediaUrl == null && MEDIA.matcher(url).matches()) {
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
        String path = state.optString("p");
        String title = state.optString("t");
        boolean verifying = title.contains("Verifica");

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
