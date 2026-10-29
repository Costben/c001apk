package app.awavenue.c001apk

import android.app.Application
import android.os.Build
import app.awavenue.c001apk.data.AccountStore
import app.awavenue.c001apk.data.AppSettings
import app.awavenue.c001apk.data.BlacklistStore
import app.awavenue.c001apk.data.ContentFilterStore
import app.awavenue.c001apk.data.ContentGate
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.DraftStore
import app.awavenue.c001apk.data.HistoryStore
import app.awavenue.c001apk.data.LocalFollowStore
import app.awavenue.c001apk.data.ProductComparisonStore
import app.awavenue.c001apk.data.SearchHistoryStore
import app.awavenue.c001apk.data.SessionStore
import app.awavenue.c001apk.data.UnreadStore
import app.awavenue.c001apk.data.UpdateService
import app.awavenue.c001apk.network.ApiClient
import app.awavenue.c001apk.platform.MessageCheckWorker
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class C001apkApp : Application(), SingletonImageLoader.Factory {
    lateinit var settings: AppSettings
        private set
    lateinit var session: SessionStore
        private set
    lateinit var accounts: AccountStore
        private set
    lateinit var localFollow: LocalFollowStore
        private set
    lateinit var updates: UpdateService
        private set
    lateinit var blacklist: BlacklistStore
        private set
    lateinit var contentFilter: ContentFilterStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var drafts: DraftStore
        private set
    lateinit var searchHistory: SearchHistoryStore
        private set
    lateinit var productComparison: ProductComparisonStore
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var repository: CoolapkRepository
        private set
    lateinit var unread: UnreadStore
        private set

    private val httpClient = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        session = SessionStore(this)
        accounts = AccountStore(this, session)
        localFollow = LocalFollowStore(this)
        blacklist = BlacklistStore(this)
        contentFilter = ContentFilterStore(this)
        history = HistoryStore(this)
        drafts = DraftStore(this)
        searchHistory = SearchHistoryStore(this)
        productComparison = ProductComparisonStore()
        apiClient = ApiClient(settings, session::snapshot, baseClient = httpClient)
        repository = CoolapkRepository(apiClient, settings, session, accounts)
        unread = UnreadStore(this, repository, session)
        updates = UpdateService(settings, BuildConfig.VERSION_NAME, httpClient)
        MessageCheckWorker.schedule(this)
    }

    /**
     * The block list and the content rules as one per-row decision, compiled for the preferences
     * in effect right now. Lists build it per page, so a rule change applies on the next load.
     */
    fun contentGate(): ContentGate = ContentGate(blacklist, contentFilter.preferences)

    // image.coolapk.com answers generic client agents with HTTP 567, so images go out with the
    // same CoolMarket agent as API calls.
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imageClient = httpClient.newBuilder()
            // The API client's 5s read timeout is tuned for JSON; a picture on a weak network
            // needs longer, and cutting it off left the thumbnail slot permanently blank.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", settings.deviceParams.userAgent).build())
            }
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
    }
}
