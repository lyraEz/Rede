.class public final Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;
.super Landroid/webkit/WebViewClient;
.implements Ljava/lang/Runnable;

.field private final context:Landroid/content/Context;
.field private final baseUrl:Ljava/lang/String;
.field private final url:Ljava/lang/String;
.field private final latch:Ljava/util/concurrent/CountDownLatch;
.field private volatile result:Ljava/lang/String;
.field private volatile error:Ljava/lang/String;
.field private volatile cleanup:Z
.field private view:Landroid/webkit/WebView;

.method public constructor <init>(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
    .locals 2
    invoke-direct {p0}, Landroid/webkit/WebViewClient;-><init>()V
    iput-object p1, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->context:Landroid/content/Context;
    iput-object p2, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->baseUrl:Ljava/lang/String;
    iput-object p3, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->url:Ljava/lang/String;
    new-instance v0, Ljava/util/concurrent/CountDownLatch;
    const/4 v1, 0x1
    invoke-direct {v0, v1}, Ljava/util/concurrent/CountDownLatch;-><init>(I)V
    iput-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->latch:Ljava/util/concurrent/CountDownLatch;
    return-void
.end method

.method public fetch()Ljava/lang/String;
    .locals 6
    new-instance v0, Landroid/os/Handler;
    invoke-static {}, Landroid/os/Looper;->getMainLooper()Landroid/os/Looper;
    move-result-object v1
    invoke-direct {v0, v1}, Landroid/os/Handler;-><init>(Landroid/os/Looper;)V
    invoke-virtual {v0, p0}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    iget-object v1, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->latch:Ljava/util/concurrent/CountDownLatch;
    const-wide/16 v2, 0x19
    sget-object v4, Ljava/util/concurrent/TimeUnit;->SECONDS:Ljava/util/concurrent/TimeUnit;
    invoke-virtual {v1, v2, v3, v4}, Ljava/util/concurrent/CountDownLatch;->await(JLjava/util/concurrent/TimeUnit;)Z
    move-result v1

    const/4 v2, 0x1
    iput-boolean v2, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->cleanup:Z
    invoke-virtual {v0, p0}, Landroid/os/Handler;->post(Ljava/lang/Runnable;)Z

    if-nez v1, :finished
    new-instance v0, Ljava/io/IOException;
    const-string v1, "A verificação do navegador demorou demais"
    invoke-direct {v0, v1}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V
    throw v0

    :finished
    iget-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->error:Ljava/lang/String;
    if-eqz v0, :success
    new-instance v1, Ljava/io/IOException;
    invoke-direct {v1, v0}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V
    throw v1

    :success
    iget-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->result:Ljava/lang/String;
    if-eqz v0, :empty_response
    return-object v0

    :empty_response
    new-instance v0, Ljava/io/IOException;
    const-string v1, "Resposta vazia da API de reprodução"
    invoke-direct {v0, v1}, Ljava/io/IOException;-><init>(Ljava/lang/String;)V
    throw v0
.end method

.method public run()V
    .locals 4
    iget-boolean v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->cleanup:Z
    if-eqz v0, :create
    iget-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->view:Landroid/webkit/WebView;
    if-eqz v0, :done
    invoke-virtual {v0}, Landroid/webkit/WebView;->stopLoading()V
    invoke-virtual {v0}, Landroid/webkit/WebView;->destroy()V
    const/4 v0, 0x0
    iput-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->view:Landroid/webkit/WebView;
    :done
    return-void

    :create
    new-instance v0, Landroid/webkit/WebView;
    iget-object v1, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->context:Landroid/content/Context;
    invoke-direct {v0, v1}, Landroid/webkit/WebView;-><init>(Landroid/content/Context;)V
    iput-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->view:Landroid/webkit/WebView;
    invoke-virtual {v0}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;
    move-result-object v1
    const/4 v2, 0x1
    invoke-virtual {v1, v2}, Landroid/webkit/WebSettings;->setJavaScriptEnabled(Z)V
    invoke-virtual {v1, v2}, Landroid/webkit/WebSettings;->setDomStorageEnabled(Z)V
    const-string v1, "RedeBridge"
    invoke-virtual {v0, p0, v1}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V
    invoke-virtual {v0, p0}, Landroid/webkit/WebView;->setWebViewClient(Landroid/webkit/WebViewClient;)V
    new-instance v1, Ljava/lang/StringBuilder;
    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V
    iget-object v2, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->baseUrl:Ljava/lang/String;
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v2, "/browse"
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Landroid/webkit/WebView;->loadUrl(Ljava/lang/String;)V
    return-void
.end method

.method public onPageFinished(Landroid/webkit/WebView;Ljava/lang/String;)V
    .locals 4
    invoke-super {p0, p1, p2}, Landroid/webkit/WebViewClient;->onPageFinished(Landroid/webkit/WebView;Ljava/lang/String;)V
    iget-boolean v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->cleanup:Z
    if-nez v0, :return
    invoke-virtual {p1}, Landroid/webkit/WebView;->getTitle()Ljava/lang/String;
    move-result-object v0
    if-eqz v0, :fetch
    const-string v1, "Attention Required"
    invoke-virtual {v0, v1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v1
    if-nez v1, :return
    const-string v1, "Just a moment"
    invoke-virtual {v0, v1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z
    move-result v1
    if-nez v1, :return

    :fetch
    iget-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->url:Ljava/lang/String;
    invoke-static {v0}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0
    new-instance v1, Ljava/lang/StringBuilder;
    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V
    const-string v2, "(function(){fetch("
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    const-string v2, ",{credentials:'include',cache:'no-store',headers:{Accept:'application/json'}}).then(function(r){return r.text().then(function(t){RedeBridge.done(r.status,t)})}).catch(function(e){RedeBridge.done(0,String(e))})})()"
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v1
    const/4 v2, 0x0
    invoke-virtual {p1, v1, v2}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    :return
    return-void
.end method

.method public done(ILjava/lang/String;)V
    .locals 3
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    const/16 v0, 0xc8
    if-lt p1, v0, :error
    const/16 v0, 0x12c
    if-ge p1, v0, :error
    iput-object p2, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->result:Ljava/lang/String;
    goto :countdown

    :error
    new-instance v0, Ljava/lang/StringBuilder;
    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
    const-string v1, "API de reprodução no navegador: HTTP "
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    iput-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->error:Ljava/lang/String;

    :countdown
    iget-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->latch:Ljava/util/concurrent/CountDownLatch;
    invoke-virtual {v0}, Ljava/util/concurrent/CountDownLatch;->countDown()V
    return-void
.end method
