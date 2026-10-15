.class public final Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserInterceptor;
.super Ljava/lang/Object;
.implements Lokhttp3/Interceptor;

.field private final context:Landroid/content/Context;
.field private final baseUrl:Ljava/lang/String;

.method public constructor <init>(Landroid/content/Context;Ljava/lang/String;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserInterceptor;->context:Landroid/content/Context;
    iput-object p2, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserInterceptor;->baseUrl:Ljava/lang/String;
    return-void
.end method

.method public intercept(Lokhttp3/Interceptor$Chain;)Lokhttp3/Response;
    .locals 7
    invoke-interface {p1}, Lokhttp3/Interceptor$Chain;->request()Lokhttp3/Request;
    move-result-object v0
    invoke-virtual {v0}, Lokhttp3/Request;->url()Lokhttp3/HttpUrl;
    move-result-object v1
    invoke-virtual {v1}, Lokhttp3/HttpUrl;->encodedPath()Ljava/lang/String;
    move-result-object v2
    const-string v3, "/api/play-link"
    invoke-virtual {v3, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v2
    if-nez v2, :browser_fetch
    invoke-interface {p1, v0}, Lokhttp3/Interceptor$Chain;->proceed(Lokhttp3/Request;)Lokhttp3/Response;
    move-result-object v0
    return-object v0

    :browser_fetch
    new-instance v2, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;
    iget-object v3, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserInterceptor;->context:Landroid/content/Context;
    iget-object v4, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserInterceptor;->baseUrl:Ljava/lang/String;
    invoke-virtual {v1}, Lokhttp3/HttpUrl;->toString()Ljava/lang/String;
    move-result-object v1
    invoke-direct {v2, v3, v4, v1}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;-><init>(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
    invoke-virtual {v2}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserFetcher;->fetch()Ljava/lang/String;
    move-result-object v1

    sget-object v2, Lokhttp3/ResponseBody;->Companion:Lokhttp3/ResponseBody$Companion;
    const/4 v3, 0x0
    invoke-virtual {v2, v1, v3}, Lokhttp3/ResponseBody$Companion;->create(Ljava/lang/String;Lokhttp3/MediaType;)Lokhttp3/ResponseBody;
    move-result-object v1

    new-instance v2, Lokhttp3/Response$Builder;
    invoke-direct {v2}, Lokhttp3/Response$Builder;-><init>()V
    invoke-virtual {v2, v0}, Lokhttp3/Response$Builder;->request(Lokhttp3/Request;)Lokhttp3/Response$Builder;
    move-result-object v2
    sget-object v3, Lokhttp3/Protocol;->HTTP_1_1:Lokhttp3/Protocol;
    invoke-virtual {v2, v3}, Lokhttp3/Response$Builder;->protocol(Lokhttp3/Protocol;)Lokhttp3/Response$Builder;
    move-result-object v2
    const/16 v3, 0xc8
    invoke-virtual {v2, v3}, Lokhttp3/Response$Builder;->code(I)Lokhttp3/Response$Builder;
    move-result-object v2
    const-string v3, "OK"
    invoke-virtual {v2, v3}, Lokhttp3/Response$Builder;->message(Ljava/lang/String;)Lokhttp3/Response$Builder;
    move-result-object v2
    invoke-virtual {v2, v1}, Lokhttp3/Response$Builder;->body(Lokhttp3/ResponseBody;)Lokhttp3/Response$Builder;
    move-result-object v2
    invoke-virtual {v2}, Lokhttp3/Response$Builder;->build()Lokhttp3/Response;
    move-result-object v0
    return-object v0
.end method
