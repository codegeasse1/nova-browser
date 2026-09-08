/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package io.github.forkmaintainers.iceraven.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonsProvider
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.kotlin.sanitizeFileName
import mozilla.components.support.ktx.kotlin.sanitizeURL
import mozilla.components.support.ktx.util.readAndDeserialize
import mozilla.components.support.ktx.util.writeString
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.fenix.Config
import org.mozilla.fenix.ext.components
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal const val API_VERSION = "api/v4"
internal const val DEFAULT_SERVER_URL = "https://services.addons.mozilla.org"


internal const val COLLECTION_FILE_NAME_PREFIX = "%s_components_addon_collection"
internal const val COLLECTION_FILE_NAME = "%s_components_addon_collection_%s.json"
internal const val COLLECTION_FILE_NAME_WITH_LANGUAGE = "%s_components_addon_collection_%s_%s.json"
internal const val REGEX_FILE_NAMES = "%s_components_addon_collection(_\\w+)?_%s.json"
internal const val MINUTE_IN_MS = 60 * 1000
internal const val DEFAULT_READ_TIMEOUT_IN_SECONDS = 20L

/**
 * Implement an add-ons provider that uses the AMO API.
 *
 * @property context A reference to the application context.
 * @property client A [Client] for interacting with the AMO HTTP api.
 * @property serverURL The url of the endpoint to interact with e.g production, staging
 * or testing. Defaults to [DEFAULT_SERVER_URL].
 * @property maxCacheAgeInMinutes maximum time (in minutes) the cached featured add-ons
 * should remain valid before a refresh is attempted. Defaults to -1, meaning no cache
 * is being used by default
 * @property ioDispatcher Coroutine dispatcher for IO operations.
 */
class PagedAMOAddonsProvider(
    private val context: Context,
    private val client: Client,
    private val collectionUser: String = "",
    private val collectionName: String = "",
    private val serverURL: String = DEFAULT_SERVER_URL,
    private val sortOption: SortOption = SortOption.POPULARITY_DESC,
    private val maxCacheAgeInMinutes: Long = -1,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonsProvider {

    private val logger = Logger("PagedAddonCollectionProvider")

    private val diskCacheLock = Any()

    private val scope = CoroutineScope(Dispatchers.IO)

    // Acts as an in-memory cache for the fetched addon's icons.
    @VisibleForTesting
    internal val iconsCache = ConcurrentHashMap<String, Bitmap>()

    /**
     * Interacts with the collections endpoint to provide a list of available
     * add-ons. May return a cached response, if [allowCache] is true, and the
     * cache is not expired (see [maxCacheAgeInMinutes]) or fetching from AMO
     * failed.
     *
     * See: https://addons-server.readthedocs.io/en/latest/topics/api/collections.html
     *
     * @param allowCache whether or not the result may be provided
     * from a previously cached response, defaults to true. Note that
     * [maxCacheAgeInMinutes] must be set for the cache to be active.
     * @param readTimeoutInSeconds optional timeout in seconds to use when fetching
     * available add-ons from a remote endpoint. If not specified [DEFAULT_READ_TIMEOUT_IN_SECONDS]
     * will be used.
     * @param language indicates in which language the translatable fields should be in, if no
     * matching language is found then a fallback translation is returned using the default language.
     * When it is null all translations available will be returned.
     * @throws IOException if the request failed, or could not be executed due to cancellation,
     * a connectivity problem or a timeout.
     */
    @Throws(IOException::class)
    @Suppress("NestedBlockDepth")
    override suspend fun getFeaturedAddons(
        allowCache: Boolean,
        readTimeoutInSeconds: Long?,
        language: String?,
    ): List<Addon> = withContext(ioDispatcher) {
        // We want to make sure we always use useFallbackFile = false here, as it warranties
        // that we are trying to fetch the latest localized add-ons when the user changes
        // language from the previous one.
        val cachedFeaturedAddons = if (allowCache && !cacheExpired(context, language, useFallbackFile = false)) {
            readFromDiskCache(language, useFallbackFile = false)?.loadIcons()
        } else {
            null
        }

        val collectionAccount = getCollectionAccount()
        val collectionName = getCollectionName()
        if (cachedFeaturedAddons != null) {
            logger.info("Providing cached list of addons for $collectionAccount collection $collectionName")
            return@withContext cachedFeaturedAddons
        }
        logger.info("Fetching fresh list of addons for $collectionAccount collection $collectionName")

        return@withContext try {
            fetchFeaturedAddons(readTimeoutInSeconds, language)
        } catch (e: IOException) {
            logger.error("Failed to fetch available add-ons", e)
            if (allowCache) {
                val cacheLastUpdated = getCacheLastUpdated(context, language, useFallbackFile = true)
                if (cacheLastUpdated > -1) {
                    val cache = readFromDiskCache(language, useFallbackFile = true)
                    cache?.let {
                        logger.info(
                            "Falling back to available add-ons cache from ${
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US).format(cacheLastUpdated)
                            }",
                        )
                        return@withContext it
                    }
                }
            }
            throw e
        }
    }

    /**
     * Interacts with the AMO search endpoint to get an addon based on any of its identifier.
     *
     * See: https://addons-server.readthedocs.io/en/latest/topics/api/addons.html#search
     *
     * @param id of the addon for which to get the download URL.
     * Can be an addon's ID, GUID, or other unique identifier.
     * @param readTimeoutInSeconds optional timeout in seconds to use when fetching the add-ons.
     * @param language indicates in which language the translatable fields should be in, if no
     * matching language is found then a fallback translation is returned using the default language.
     * When it is null all translations available will be returned.
     *
     * @return an [Addon] configuration if it was successfully downloaded or null otherwise.
     */
    override suspend fun getAddonByID(
        id: String,
        readTimeoutInSeconds: Long?,
        language: String?,
    ): Addon? {
        val langParam = when (!language.isNullOrEmpty()) {
            true -> "&lang=$language"
            else -> ""
        }

        return client.fetch(
            Request(
                url = "$serverURL/$API_VERSION/addons/search/?guid=$id$langParam",
                readTimeout = Pair(readTimeoutInSeconds ?: DEFAULT_READ_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS),
            ),
        ).use { response ->
            if (response.isSuccess) {
                val responseBody = response.body.string(Charsets.UTF_8)
                try {
                    JSONObject(responseBody)
                        .getAddonsFromSearchResults(language)
                        .firstOrNull()
                } catch (e: JSONException) {
                    logger.error("Failed to get addon by uuid [$id]", e)
                    null
                }
            } else {
                logger.error("Failed to get addon by uuid [$id]. Status code: ${response.status}")
                null
            }
        }
    }

    /**
     * Fetches all pages of add-ons from the given URL (following the "next"
     * field in the returned JSON) and combines the "results" arrays into that
     * of the first page. Returns that coalesced object.
     *
     * @param readTimeoutInSeconds timeout in seconds to use when fetching each page.
     * @param language indicates in which language the translatable fields should be in, if no
     * matching language is found then a fallback translation is returned using the default language.
     * When it is null all translations available will be returned.
     * @throws IOException if the request failed, or could not be executed due to cancellation,
     * a connectivity problem or a timeout.
     */
    @Throws(IOException::class)
    private suspend fun fetchFeaturedAddons(
        readTimeoutInSeconds: Long?,
        language: String?,
    ): List<Addon> = withContext(ioDispatcher) {
        val langParam = if (!language.isNullOrEmpty()) {
            "&lang=$language"
        } else {
            ""
        }
        val collectionAccount = getCollectionAccount()
        val collectionName = getCollectionName()
        var url = listOf(
            serverURL,
            API_VERSION,
            "accounts/account",
            collectionAccount,
            "collections",
            collectionName,
            "addons",
            "?sort=${sortOption.value}",
            langParam,
        ).joinToString("/")
        // Fetch and compile all the pages into one object we can return
        var compiledResponse = JSONObject()
        // Each page tells us where to get the next page, if there is one
        var nextURL: String? = url
        logger.debug("Fetching URI: $nextURL")
        while (nextURL != null) {
            client.fetch(
                Request(
                    url = nextURL,
                    readTimeout = Pair(readTimeoutInSeconds ?: DEFAULT_READ_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS),
                    conservative = false,
                ),
            )
                .use { response ->
                    if (response.isSuccess) {
                        val currentResponse = JSONObject(response.body.string(Charsets.UTF_8))
                        if (compiledResponse.length() == 0) {
                            compiledResponse = currentResponse
                        } else {
                            // Write the addons into the first response
                            compiledResponse.getJSONArray("results")
                                .concat(currentResponse.getJSONArray("results"))
                        }
                        nextURL = if (currentResponse.isNull("next")) null else currentResponse.getString("next")
                    } else {
                        val errorMessage = "Failed to fetch featured add-ons from collection. " +
                            "Status code: ${response.status}"
                        logger.error(errorMessage)
                        throw IOException(errorMessage)
                    }
                }
        }
        return@withContext try {
            compiledResponse
                .getAddonsFromCollection(language)
                .loadIcons()
                .also {
                    // Cache the JSON object before we parse out the addons
                    if (maxCacheAgeInMinutes > 0) {
                        writeToDiskCache(it.toString(), language)
                    }
                    deleteUnusedCacheFiles(language)
                }
        } catch (e: JSONException) {
            throw IOException(e)
        }
    }

    /**
     * Asynchronously loads add-on icon for the given [iconUrl] and stores in the cache.
     */
    @VisibleForTesting
    internal fun loadIconAsync(addonId: String, iconUrl: String): Deferred<Bitmap?> = scope.async {
        val cachedIcon = iconsCache[addonId]
        if (cachedIcon != null) {
            logger.info("Icon for $addonId was found in the cache")
            cachedIcon
        } else if (iconUrl.isBlank()) {
            logger.info("Unable to find the icon for $addonId blank iconUrl")
            null
        } else {
            try {
                logger.info("Trying to fetch the icon for $addonId from the network")
                client.fetch(Request(url = iconUrl.sanitizeURL(), useCaches = true, conservative = true))
                    .use { response ->
                        if (response.isSuccess) {
                            response.body.useStream {
                                val icon = BitmapFactory.decodeStream(it)
                                logger.info("Icon for $addonId fetched from the network")
                                iconsCache[addonId] = icon
                                icon
                            }
                        } else {
                            // There was an network error and we couldn't fetch the icon.
                            logger.info("Unable to fetch the icon for $addonId HTTP code ${response.status}")
                            null
                        }
                    }
            } catch (e: IOException) {
                logger.error("Attempt to fetch the $addonId icon failed", e)
                null
            }
        }
    }

    @VisibleForTesting
    internal suspend fun List<Addon>.loadIcons(): List<Addon> {
        this.map {
            // Instead of loading icons one by one, let's load them async
            // so we can do multiple request at the time.
            loadIconAsync(it.id, it.iconUrl)
        }.awaitAll() // wait until all parallel icon requests finish.

        return this.map { addon ->
            addon.copy(icon = iconsCache[addon.id])
        }
    }

    @VisibleForTesting
    internal fun writeToDiskCache(collectionResponse: String, language: String?) {
        logger.info("Storing cache file")
        synchronized(diskCacheLock) {
            getCacheFile(context, language, useFallbackFile = false).writeString { collectionResponse }
        }
    }

    @VisibleForTesting
    internal suspend fun readFromDiskCache(
        language: String?,
        useFallbackFile: Boolean,
    ): List<Addon>? = withContext(ioDispatcher) {
        logger.info("Loading cache file")
        synchronized(diskCacheLock) {
            return@withContext getCacheFile(context, language, useFallbackFile).readAndDeserialize {
                JSONObject(it).getAddonsFromCollection(language)
            }
        }
    }

    /**
     * Deletes cache files from previous (now unused) collections.
     */
    @VisibleForTesting
    internal fun deleteUnusedCacheFiles(language: String?) {
        val currentCacheFileName = getBaseCacheFile(context, language, useFallbackFile = true).name

        context.filesDir
            .listFiles { _, s ->
                s.startsWith(COLLECTION_FILE_NAME_PREFIX.format(getCollectionAccount())) && s != currentCacheFileName
            }
            ?.forEach {
                logger.debug("Deleting unused collection cache: " + it.name)
                it.delete()
            }
    }

    @VisibleForTesting
    internal fun cacheExpired(context: Context, language: String?, useFallbackFile: Boolean): Boolean {
        return getCacheLastUpdated(
            context,
            language,
            useFallbackFile,
        ) < Date().time - maxCacheAgeInMinutes * MINUTE_IN_MS
    }

    @VisibleForTesting
    internal fun getCacheLastUpdated(context: Context, language: String?, useFallbackFile: Boolean): Long {
        val file = getBaseCacheFile(context, language, useFallbackFile)
        return if (file.exists()) file.lastModified() else -1
    }

    private fun getCacheFile(context: Context, language: String?, useFallbackFile: Boolean): AtomicFile {
        return AtomicFile(getBaseCacheFile(context, language, useFallbackFile))
    }

    @VisibleForTesting
    internal fun getBaseCacheFile(context: Context, language: String?, useFallbackFile: Boolean): File {
        val collectionAccount = getCollectionAccount()
        val collectionName = getCollectionName()
        var file = File(context.filesDir, getCacheFileName(language))
        if (!file.exists() && useFallbackFile) {
            // In situations, where users change languages and we can't retrieve the new one,
            // we always want to fallback to the previous localized file.
            // Try to find first available localized file.
            val regex = Regex(REGEX_FILE_NAMES.format(collectionAccount, collectionName))
            val fallbackFile = context.filesDir.listFiles()?.find { it.name.matches(regex) }

            if (fallbackFile?.exists() == true) {
                file = fallbackFile
            }
        }
        return file
    }

    @VisibleForTesting
    internal fun getCacheFileName(language: String? = ""): String {
        val collectionAccount = getCollectionAccount()
        val collectionName = getCollectionName()

        val fileName = if (language.isNullOrEmpty()) {
            COLLECTION_FILE_NAME.format(collectionAccount, collectionName)
        } else {
            COLLECTION_FILE_NAME_WITH_LANGUAGE.format(collectionAccount, language, collectionName)
        }
        return fileName.sanitizeFileName()
    }

    /**
     * Get the collection name we should be fetching addons from.
     */
    @VisibleForTesting
    internal fun getCollectionName(): String {
        if (collectionName.isNotEmpty()) {
            return collectionName
        }
        var result = context.components.settings.customAddonsCollection
        if (Config.channel.isNightlyOrDebug && context.components.settings
                .amoCollectionOverrideConfigured()
        ) {
            result = context.components.settings.overrideAmoCollection
        }
        logger.info("Determined collection name: $result")
        return result
    }

    /**
     * Get the account we should be fetching addons from.
     */
    @VisibleForTesting
    internal fun getCollectionAccount(): String {
        if (collectionUser.isNotEmpty()) {
            return collectionUser
        }
        var result = context.components.settings.customAddonsAccount
        if (Config.channel.isNightlyOrDebug && context.components.settings
                .amoCollectionOverrideConfigured()
        ) {
            result = context.components.settings.overrideAmoUser
        }
        logger.info("Determined collection account: $result")
        return result
    }

    fun deleteCacheFile() {
        logger.info("Clearing cache file")
        synchronized(diskCacheLock) {
            //val file = getBaseCacheFile(context, language, useFallbackFile = true)
            //return if (file.exists()) file.delete() else false
            context.filesDir.listFiles { _, s ->
                s.contains("components_addon_collection")
            }?.forEach {
                logger.debug("Deleting collection files ${it.name}")
                it.delete()
            }
        }
    }
}

/**
 * Represents possible sort options for the recommended add-ons from
 * the configured add-on collection.
 */
enum class SortOption(val value: String) {
    POPULARITY("popularity"),
    POPULARITY_DESC("-popularity"),
    NAME("name"),
    NAME_DESC("-name"),
    DATE_ADDED("added"),
    DATE_ADDED_DESC("-added"),
}

internal fun JSONObject.getAddonsFromSearchResults(language: String? = null): List<Addon> {
    val addonsJson = getJSONArray("results")
    return (0 until addonsJson.length()).map { index ->
        addonsJson.getJSONObject(index).toAddon(language)
    }
}

internal fun JSONObject.getAddonsFromCollection(language: String? = null): List<Addon> {
    val addonsJson = getJSONArray("results")
    // Each result in a collection response has an `addon` key and some (optional) notes.
    return (0 until addonsJson.length()).map { index ->
        addonsJson.getJSONObject(index).getJSONObject("addon").toAddon(language)
    }
}

internal fun JSONObject.toAddon(language: String? = null): Addon {
    return with(this) {
        val safeLanguage = language?.lowercase(Locale.getDefault())
        val summary = getSafeTranslations("summary", safeLanguage)
        val isLanguageInTranslations = summary.containsKey(safeLanguage)
        Addon(
            id = getSafeString("guid"),
            author = getAuthor(),
            createdAt = getSafeString("created"),
            updatedAt = getCurrentVersionCreated(),
            downloadUrl = getDownloadUrl(),
            version = getCurrentVersion(),
            permissions = getPermissions(),
            translatableName = getSafeTranslations("name", safeLanguage),
            translatableDescription = getSafeTranslations("description", safeLanguage),
            translatableSummary = summary,
            iconUrl = getSafeString("icon_url"),
            // This isn't the add-on homepage but the URL to the AMO detail page. On AMO, the homepage is
            // a translatable field but https://github.com/mozilla/addons-server/issues/21310 prevents us
            // from retrieving the homepage URL of any add-on reliably.
            homepageUrl = getSafeString("url"),
            rating = getRating(),
            ratingUrl = getSafeString("ratings_url"),
            detailUrl = getSafeString("url"),
            defaultLocale = (
                if (!safeLanguage.isNullOrEmpty() && isLanguageInTranslations) {
                    safeLanguage
                } else {
                    getSafeString("default_locale").ifEmpty { Addon.DEFAULT_LOCALE }
                }
                ).lowercase(Locale.ROOT),
        )
    }
}

internal fun JSONObject.getRating(): Addon.Rating? {
    val jsonRating = optJSONObject("ratings")
    return if (jsonRating != null) {
        Addon.Rating(
            reviews = jsonRating.optInt("text_count"),
            average = jsonRating.optDouble("average").toFloat(),
        )
    } else {
        null
    }
}

internal fun JSONObject.getPermissions(): List<String> {
    val permissionsJson = getFile()?.getSafeJSONArray("permissions") ?: JSONArray()
    return (0 until permissionsJson.length()).map { index ->
        permissionsJson.getString(index)
    }
}

internal fun JSONObject.getCurrentVersion(): String {
    return getJSONObject("current_version").getSafeString("version")
}

internal fun JSONObject.getFile(): JSONObject? {
    return getJSONObject("current_version")
        .getSafeJSONArray("files")
        .optJSONObject(0)
}

internal fun JSONObject.getCurrentVersionCreated(): String {
    // We want to return: `current_version.files[0].created`.
    return getFile()?.getSafeString("created").orEmpty()
}

internal fun JSONObject.getDownloadUrl(): String {
    return getFile()?.getSafeString("url").orEmpty()
}

internal fun JSONObject.getAuthor(): Addon.Author? {
    val authorsJson = getSafeJSONArray("authors")
    // We only consider the first author in the AMO API response, mainly because Gecko does the same.
    val authorJson = authorsJson.optJSONObject(0)

    return if (authorJson != null) {
        Addon.Author(
            name = authorJson.getSafeString("name"),
            url = authorJson.getSafeString("url"),
        )
    } else {
        null
    }
}

internal fun JSONObject.getSafeString(key: String): String {
    return if (isNull(key)) {
        ""
    } else {
        getString(key)
    }
}

internal fun JSONObject.getSafeJSONArray(key: String): JSONArray {
    return if (isNull(key)) {
        JSONArray("[]")
    } else {
        getJSONArray(key)
    }
}

internal fun JSONObject.getSafeTranslations(valueKey: String, language: String?): Map<String, String> {
    // We can have two different versions of the JSON structure for translatable fields:
    // 1) A string with only one language, when we provide a language parameter.
    // 2) An object containing all the languages available when a language parameter is NOT present.
    // For this reason, we have to be specific about how we parse the JSON.
    return if (get(valueKey) is String) {
        val safeLanguage = (language ?: Addon.DEFAULT_LOCALE).lowercase(Locale.ROOT)
        mapOf(safeLanguage to getSafeString(valueKey))
    } else {
        getSafeMap(valueKey)
    }
}

internal fun JSONObject.getSafeMap(valueKey: String): Map<String, String> {
    return if (isNull(valueKey)) {
        emptyMap()
    } else {
        val map = mutableMapOf<String, String>()
        val jsonObject = getJSONObject(valueKey)

        jsonObject.keys()
            .forEach { key ->
                map[key.lowercase(Locale.ROOT)] = jsonObject.getSafeString(key)
            }
        map
    }
}

/**
 * Concatenates the given JSONArray onto this one.
 */
internal fun JSONArray.concat(other: JSONArray) {
    (0 until other.length()).map { index ->
        put(length(), other.getJSONObject(index))
    }
}
