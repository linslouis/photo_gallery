package com.morbit.photogallery

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** PhotoGalleryPlugin */
class PhotoGalleryPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    companion object {
        // This static function is optional and equivalent to onAttachedToEngine. It supports the old
        // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
        // plugin registration via this function while apps migrate to use the new Android APIs
        // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
        //
        // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
        // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
        // depending on the user's project. onAttachedToEngine or registerWith must both be defined
        // in the same class.
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "photo_gallery")
            val plugin = PhotoGalleryPlugin()
            plugin.context = registrar.activeContext()
            channel.setMethodCallHandler(plugin)
        }

        const val imageType = "image"
        const val videoType = "video"
        const val audioType = "audio"

        const val allAlbumId = "__ALL__"
        const val allAlbumName = "All"

        val imageMetadataProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val imageBriefMetadataProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED
        )

        val videoMetadataProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        val videoBriefMetadataProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED
        )
    }

    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "photo_gallery")
        val plugin = this
        plugin.context = flutterPluginBinding.applicationContext
        channel.setMethodCallHandler(plugin)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity;
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        this.activity = binding.activity;
    }

    override fun onDetachedFromActivity() {
        this.activity = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        this.activity = null
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "listAlbums" -> {
                val mediumType = call.argument<String>("mediumType")
                executor.submit {
                    result.success(
                        listAlbums(mediumType)
                    )
                }
            }

            "listMedia" -> {
                val albumId = call.argument<String>("albumId")
                val mediumType = call.argument<String>("mediumType")
                val newest = call.argument<Boolean>("newest")
                val skip = call.argument<Int>("skip")
                val take = call.argument<Int>("take")
                val lightWeight = call.argument<Boolean>("lightWeight")
                executor.submit {
                    result.success(
                        listMedia(mediumType, albumId!!, newest!!, skip, take, lightWeight)
                    )
                }
            }

            "getMedium" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                executor.submit {
                    result.success(
                        getMedium(mediumId!!, mediumType)
                    )
                }
            }

            "getThumbnail" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                val width = call.argument<Int>("width")
                val height = call.argument<Int>("height")
                val highQuality = call.argument<Boolean>("highQuality")
                executor.submit {
                    result.success(
                        getThumbnail(mediumId!!, mediumType, width, height, highQuality)
                    )
                }
            }

            "getAlbumThumbnail" -> {
                val albumId = call.argument<String>("albumId")
                val mediumType = call.argument<String>("mediumType")
                val newest = call.argument<Boolean>("newest")
                val width = call.argument<Int>("width")
                val height = call.argument<Int>("height")
                val highQuality = call.argument<Boolean>("highQuality")
                executor.submit {
                    result.success(
                        getAlbumThumbnail(albumId!!, mediumType, newest!!, width, height, highQuality)
                    )
                }
            }

            "getFile" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                val mimeType = call.argument<String>("mimeType")
                executor.submit {
                    result.success(
                        getFile(mediumId!!, mediumType, mimeType)
                    )
                }
            }

            "deleteMedium" -> {
                val mediumId = call.argument<String>("mediumId")
                val mediumType = call.argument<String>("mediumType")
                executor.submit {
                    result.success(
                        deleteMedium(mediumId!!, mediumType)
                    )
                }
            }

            "cleanCache" -> {
                executor.submit {
                    result.success(
                        cleanCache()
                    )
                }
            }

            //get all music files
            "getAllMusicFiles" -> {
                val musicFiles = getAllMusicFiles()

                val originalJsonObject = JSONObject(musicFiles.toString())

                // Get the root folder path ("/storage/emulated/0/")
                val rootFolderPath = "/storage/emulated/0/"

                // Create a new JSONObject for the modified structure
                val modifiedJsonObject = JSONObject()

                // Add the "start" attribute to the modified JSON
                modifiedJsonObject.put("start", originalJsonObject.getInt("start"))

                // Create an array to store the items in the modified structure
                val modifiedItems = mutableListOf<JSONObject>()

                // Iterate through the original items
                val originalItems = originalJsonObject.getJSONArray("items")
                for (i in 0 until originalItems.length()) {
                    val originalItem = originalItems.getJSONObject(i)

                    // Extract the folder path of the item
                    val itemData = originalItem.getString("data")
                    val folderPath = itemData.substring(rootFolderPath.length, itemData.lastIndexOf('/'))

                    // Create a new JSONObject for the audio item
                    val audioItem = JSONObject()
                    audioItem.put("id", originalItem.getInt("id"))
                    audioItem.put("displayName", originalItem.getString("displayName"))
                    audioItem.put("title", originalItem.getString("title"))
                    audioItem.put("size", originalItem.getLong("size"))
                    audioItem.put("mimeType", originalItem.getString("mimeType"))
                    audioItem.put("dateAdded", originalItem.getLong("dateAdded"))
                    audioItem.put("dateModified", originalItem.getLong("dateModified"))
                    audioItem.put("album", originalItem.getString("album"))
                    audioItem.put("artist", originalItem.getString("artist"))
                    audioItem.put("duration", originalItem.getLong("duration"))
                    audioItem.put("data", itemData)

                    // Check if the folder already exists in the modified structure
                    val folderIndex = modifiedItems.indexOfFirst { it.getString("folderPath") == folderPath }

                    if (folderIndex == -1) {
                        // If the folder doesn't exist, create a new folder and add the audio item
                        val folder = JSONObject()
                        folder.put("folderPath", folderPath)
                        folder.put("audioItems", mutableListOf(audioItem))
                        modifiedItems.add(folder)
                    } else {
                        // If the folder exists, add the audio item to the existing folder
                        val existingFolder = modifiedItems[folderIndex]
                        val existingAudioItems = existingFolder.getJSONArray("audioItems")
                        existingAudioItems.put(audioItem)
                    }
                }

                // Add the modified items to the modified JSON
                modifiedJsonObject.put("items", modifiedItems)


                val file = File("/sdcard/Download/linslog.txt")
                file.writeText(modifiedJsonObject.toString())

                Log.e("LINSLOG", modifiedJsonObject.toString())
                result.success(modifiedJsonObject)



            }

            else -> result.notImplemented()
        }
    }

    private fun getAllMusicFiles(): Any {

        val musicProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )

        val musicCursor = this.context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            musicProjection,
            null,
            null,
            null
        )

        val musicList = mutableListOf<Map<String, Any?>>()

        musicCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val metadata = getMusicMetadata(cursor)
                musicList.add(metadata)
            }
        }

        return mapOf(
            "start" to 0,
            "items" to musicList
        )

    }

    private fun getMusicMetadata(cursor: Cursor): Map<String, Any?> {

        val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
        val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val mimeTypeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
        val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
        val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

        val id = cursor.getLong(idColumn)
        val displayName = cursor.getString(displayNameColumn)
        val title = cursor.getString(titleColumn)
        val size = cursor.getLong(sizeColumn)
        val mimeType = cursor.getString(mimeTypeColumn)
        val dateAdded = cursor.getLong(dateAddedColumn) * 1000
        val dateModified = cursor.getLong(dateModifiedColumn) * 1000
        val album = cursor.getString(albumColumn)
        val artist = cursor.getString(artistColumn)
        val duration = cursor.getLong(durationColumn)
        val data = cursor.getString(dataColumn)

        return mapOf(
            "id" to id,
            "displayName" to displayName,
            "title" to title,
            "size" to size,
            "mimeType" to mimeType,
            "dateAdded" to dateAdded,
            "dateModified" to dateModified,
            "album" to album,
            "artist" to artist,
            "duration" to duration,
            "data" to data
        )


    }

    private fun listAlbums(mediumType: String?): List<Map<String, Any?>> {
        return when (mediumType) {
            imageType -> {
                listImageAlbums().values.toList()
            }

            videoType -> {
                listVideoAlbums().values.toList()
            }

            audioType -> {
                listAudioAlbums().values.toList()
            }

            else -> {
                listAllAlbums().values.toList()
            }
        }
    }

    private fun listImageAlbums(): Map<String, Map<String, Any>> {
        this.context.run {
            var total = 0
            val albumHashMap = hashMapOf<String, HashMap<String, Any>>()

            val imageProjection = arrayOf(
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_ID
            )

            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                null
            )

            imageCursor?.use { cursor ->
                val bucketColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val bucketColumnId = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketColumnId)
                    val album = albumHashMap[bucketId]
                    if (album == null) {
                        val folderName = cursor.getString(bucketColumn)
                        albumHashMap[bucketId] = hashMapOf(
                            "id" to bucketId,
                            "name" to folderName,
                            "count" to 1
                        )
                    } else {
                        val count = album["count"] as Int
                        album["count"] = count + 1
                    }
                    total++
                }
            }

            val albumLinkedMap = linkedMapOf<String, Map<String, Any>>()
            albumLinkedMap[allAlbumId] = hashMapOf(
                "id" to allAlbumId,
                "name" to allAlbumName,
                "count" to total
            )
            albumLinkedMap.putAll(albumHashMap)
            return albumLinkedMap
        }
    }

    private fun listVideoAlbums(): Map<String, Map<String, Any>> {
        this.context.run {
            var total = 0
            val albumHashMap = hashMapOf<String, HashMap<String, Any>>()

            val videoProjection = arrayOf(
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_ID
            )

            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )

            videoCursor?.use { cursor ->
                val bucketColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val bucketColumnId = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)

                while (cursor.moveToNext()) {
                    val bucketId = cursor.getString(bucketColumnId)
                    val album = albumHashMap[bucketId]
                    if (album == null) {
                        val folderName = cursor.getString(bucketColumn)
                        albumHashMap[bucketId] = hashMapOf(
                            "id" to bucketId,
                            "name" to folderName,
                            "count" to 1
                        )
                    } else {
                        val count = album["count"] as Int
                        album["count"] = count + 1
                    }
                    total++
                }
            }

            val albumLinkedMap = linkedMapOf<String, Map<String, Any>>()
            albumLinkedMap[allAlbumId] = hashMapOf(
                "id" to allAlbumId,
                "name" to allAlbumName,
                "count" to total
            )
            albumLinkedMap.putAll(albumHashMap)
            return albumLinkedMap
        }
    }

    private fun listAudioAlbums(): Map<String, Map<String, Any>> {
        this.context.run {
            var total = 0
            val albumHashMap = hashMapOf<String, HashMap<String, Any>>()

            val audioProjection = arrayOf(
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID
            )

            val audioCursor = this.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                null
            )

            audioCursor?.use { cursor ->
                val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumColumnId = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val albumId = cursor.getString(albumColumnId)
                    val album = albumHashMap[albumId]
                    if (album == null) {
                        val albumName = cursor.getString(albumColumn)
                        albumHashMap[albumId] = hashMapOf(
                            "id" to albumId,
                            "name" to albumName,
                            "count" to 1
                        )
                    } else {
                        val count = album["count"] as Int
                        album["count"] = count + 1
                    }
                    total++
                }
            }

            val albumLinkedMap = linkedMapOf<String, Map<String, Any>>()
            albumLinkedMap[allAlbumId] = hashMapOf(
                "id" to allAlbumId,
                "name" to allAlbumName,
                "count" to total
            )
            albumLinkedMap.putAll(albumHashMap)
            return albumLinkedMap
        }
    }

    private fun listAllAlbums(): Map<String, Map<String, Any?>> {
        val imageMap = this.listImageAlbums()
        val videoMap = this.listVideoAlbums()
        val albumMap = (imageMap.keys + videoMap.keys).associateWith {
            mapOf(
                "id" to it,
                "name" to imageMap[it]?.get("name"),
                "count" to (imageMap[it]?.get("count") ?: 0) as Int + (videoMap[it]?.get("count") ?: 0) as Int,
            )
        }
        return albumMap
    }

    private fun listMedia(
        mediumType: String?,
        albumId: String,
        newest: Boolean,
        skip: Int?,
        take: Int?,
        lightWeight: Boolean? = false
    ): Map<String, Any?> {
        return when (mediumType) {
            imageType -> {
                listImages(albumId, newest, skip, take, lightWeight)
            }

            videoType -> {
                listVideos(albumId, newest, skip, take, lightWeight)
            }

            else -> {
                val images = listImages(albumId, newest, null, null, lightWeight)["items"] as List<Map<String, Any?>>
                val videos = listVideos(albumId, newest, null, null, lightWeight)["items"] as List<Map<String, Any?>>
                val comparator = compareBy<Map<String, Any?>> { it["creationDate"] as Long }
                    .thenBy { it["modifiedDate"] as Long }
                var items = (images + videos).sortedWith(comparator)
                if (newest) {
                    items = items.reversed()
                }
                if (skip != null || take != null) {
                    val start = skip ?: 0
                    val total = items.size
                    val end = if (take == null) total else Integer.min(start + take, total)
                    items = items.subList(start, end)
                }
                mapOf(
                    "start" to (skip ?: 0),
                    "items" to items
                )
            }
        }
    }

    private fun listImages(
        albumId: String,
        newest: Boolean,
        skip: Int?,
        take: Int?,
        lightWeight: Boolean? = false
    ): Map<String, Any?> {
        val media = mutableListOf<Map<String, Any?>>()

        this.context.run {
            val projection = if (lightWeight == true) imageBriefMetadataProjection else imageMetadataProjection
            val imageCursor = getImageCursor(albumId, newest, projection, skip, take)

            imageCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val metadata = if (lightWeight == true) getImageBriefMetadata(cursor) else getImageMetadata(cursor)
                    media.add(metadata)
                }
            }
        }

        return mapOf(
            "start" to (skip ?: 0),
            "items" to media
        )
    }

    private fun listVideos(
        albumId: String,
        newest: Boolean,
        skip: Int?,
        take: Int?,
        lightWeight: Boolean? = false
    ): Map<String, Any?> {
        val media = mutableListOf<Map<String, Any?>>()

        this.context.run {
            val projection = if (lightWeight == true) videoBriefMetadataProjection else videoMetadataProjection
            val videoCursor = getVideoCursor(albumId, newest, projection, skip, take)

            videoCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    val metadata = if (lightWeight == true) getVideoBriefMetadata(cursor) else getVideoMetadata(cursor)
                    media.add(metadata)
                }
            }
        }

        return mapOf(
            "start" to (skip ?: 0),
            "items" to media
        )
    }

    private fun getMedium(mediumId: String, mediumType: String?): Map<String, Any?>? {
        return when (mediumType) {
            imageType -> {
                getImageMedia(mediumId)
            }

            videoType -> {
                getVideoMedia(mediumId)
            }

            else -> {
                getImageMedia(mediumId) ?: getVideoMedia(mediumId)
            }
        }
    }

    private fun getImageMedia(mediumId: String): Map<String, Any?>? {
        return this.context.run {
            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageMetadataProjection,
                "${MediaStore.Images.Media._ID} = ?",
                arrayOf(mediumId),
                null
            )

            imageCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return@run getImageMetadata(cursor)
                }
            }

            return@run null
        }
    }

    private fun getVideoMedia(mediumId: String): Map<String, Any?>? {
        return this.context.run {
            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoMetadataProjection,
                "${MediaStore.Video.Media._ID} = ?",
                arrayOf(mediumId),
                null
            )

            videoCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return@run getVideoMetadata(cursor)
                }
            }

            return@run null
        }
    }

    private fun getThumbnail(
        mediumId: String,
        mediumType: String?,
        width: Int?,
        height: Int?,
        highQuality: Boolean?
    ): ByteArray? {
        return when (mediumType) {
            imageType -> {
                getImageThumbnail(mediumId, width, height, highQuality)
            }

            videoType -> {
                getVideoThumbnail(mediumId, width, height, highQuality)
            }

            else -> {
                getImageThumbnail(mediumId, width, height, highQuality)
                    ?: getVideoThumbnail(mediumId, width, height, highQuality)
            }
        }
    }

    private fun getImageThumbnail(mediumId: String, width: Int?, height: Int?, highQuality: Boolean?): ByteArray? {
        var byteArray: ByteArray? = null

        val bitmap: Bitmap? = this.context.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val widthSize = width ?: if (highQuality == true) 512 else 96
                    val heightSize = height ?: if (highQuality == true) 384 else 96
                    this.contentResolver.loadThumbnail(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediumId.toLong()),
                        Size(widthSize, heightSize),
                        null
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                val kind =
                    if (highQuality == true) MediaStore.Images.Thumbnails.MINI_KIND
                    else MediaStore.Images.Thumbnails.MICRO_KIND
                MediaStore.Images.Thumbnails.getThumbnail(
                    this.contentResolver, mediumId.toLong(),
                    kind, null
                )
            }
        }
        bitmap?.run {
            ByteArrayOutputStream().use { stream ->
                this.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                byteArray = stream.toByteArray()
            }
        }

        return byteArray
    }

    private fun getVideoThumbnail(mediumId: String, width: Int?, height: Int?, highQuality: Boolean?): ByteArray? {
        var byteArray: ByteArray? = null

        val bitmap: Bitmap? = this.context.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val widthSize = width ?: if (highQuality == true) 512 else 96
                    val heightSize = height ?: if (highQuality == true) 384 else 96
                    this.contentResolver.loadThumbnail(
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediumId.toLong()),
                        Size(widthSize, heightSize),
                        null
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                val kind =
                    if (highQuality == true) MediaStore.Video.Thumbnails.MINI_KIND
                    else MediaStore.Video.Thumbnails.MICRO_KIND
                MediaStore.Video.Thumbnails.getThumbnail(
                    this.contentResolver, mediumId.toLong(),
                    kind, null
                )
            }
        }
        bitmap?.run {
            ByteArrayOutputStream().use { stream ->
                this.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                byteArray = stream.toByteArray()
            }
        }

        return byteArray
    }

    private fun getAlbumThumbnail(
        albumId: String,
        mediumType: String?,
        newest: Boolean,
        width: Int?,
        height: Int?,
        highQuality: Boolean?
    ): ByteArray? {
        return when (mediumType) {
            imageType -> {
                getImageAlbumThumbnail(albumId, newest, width, height, highQuality)
            }

            videoType -> {
                getVideoAlbumThumbnail(albumId, newest, width, height, highQuality)
            }

            else -> {
                getAllAlbumThumbnail(albumId, newest, width, height, highQuality)
            }
        }
    }

    private fun getImageAlbumThumbnail(
        albumId: String,
        newest: Boolean,
        width: Int?,
        height: Int?,
        highQuality: Boolean?
    ): ByteArray? {
        return this.context.run {
            val projection = arrayOf(MediaStore.Images.Media._ID)

            val imageCursor = getImageCursor(albumId, newest, projection, null, 1)

            imageCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    return@run getImageThumbnail(id.toString(), width, height, highQuality)
                }
            }

            return@run null
        }
    }

    private fun getVideoAlbumThumbnail(
        albumId: String,
        newest: Boolean,
        width: Int?,
        height: Int?,
        highQuality: Boolean?
    ): ByteArray? {
        return this.context.run {
            val projection = arrayOf(MediaStore.Video.Media._ID)

            val videoCursor = getVideoCursor(albumId, newest, projection, null, 1)

            videoCursor?.use { cursor ->
                if (cursor.moveToNext()) {
                    val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val id = cursor.getLong(idColumn)
                    return@run getVideoThumbnail(id.toString(), width, height, highQuality)
                }
            }

            return@run null
        }
    }

    private fun getAllAlbumThumbnail(
        albumId: String,
        newest: Boolean,
        width: Int?,
        height: Int?,
        highQuality: Boolean?
    ): ByteArray? {
        return this.context.run {
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED,
            )

            val imageCursor = getImageCursor(albumId, newest, imageProjection, null, 1)

            var imageId: Long? = null
            var imageDateAdded: Long? = null
            var imageDateModified: Long? = null
            imageCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val dateAddedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                    val dateModifiedColumn =
                        cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                    imageId = cursor.getLong(idColumn)
                    imageDateAdded = cursor.getLong(dateAddedColumn) * 1000
                    imageDateModified = cursor.getLong(dateModifiedColumn) * 1000
                }
            }

            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATE_MODIFIED,
            )

            val videoCursor = getVideoCursor(albumId, newest, videoProjection, null, 1)

            var videoId: Long? = null
            var videoDateAdded: Long? = null
            var videoDateModified: Long? = null
            videoCursor?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val dateAddedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val dateModifiedColumn =
                        cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                    videoId = cursor.getLong(idColumn)
                    videoDateAdded = cursor.getLong(dateAddedColumn) * 1000
                    videoDateModified = cursor.getLong(dateModifiedColumn) * 1000
                }
            }

            if (imageId != null && videoId != null) {
                if (newest && imageDateAdded!! > videoDateAdded!! || !newest && imageDateAdded!! < videoDateAdded!!) {
                    return@run getImageThumbnail(imageId.toString(), width, height, highQuality)
                }
                if (newest && imageDateAdded!! < videoDateAdded!! || !newest && imageDateAdded!! > videoDateAdded!!) {
                    return@run getVideoThumbnail(videoId.toString(), width, height, highQuality)
                }
                if (newest && imageDateModified!! >= videoDateModified!! || !newest && imageDateModified!! <= videoDateModified!!) {
                    return@run getImageThumbnail(imageId.toString(), width, height, highQuality)
                }
                return@run getVideoThumbnail(videoId.toString(), width, height, highQuality)
            }

            if (imageId != null) {
                return@run getImageThumbnail(imageId.toString(), width, height, highQuality)
            }

            if (videoId != null) {
                return@run getVideoThumbnail(videoId.toString(), width, height, highQuality)
            }

            return@run null
        }
    }

    private fun getImageCursor(
        albumId: String,
        newest: Boolean,
        projection: Array<String>,
        skip: Int?,
        take: Int?
    ): Cursor? {
        this.context.run {
            val isSelection = albumId != allAlbumId
            val selection = if (isSelection) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null
            val selectionArgs = if (isSelection) arrayOf(albumId) else null
            val orderBy = if (newest) {
                "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            } else {
                "${MediaStore.Images.Media.DATE_ADDED} ASC, ${MediaStore.Images.Media.DATE_MODIFIED} ASC"
            }

            val imageCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    android.os.Bundle().apply {
                        // Selection
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                        // Sort
                        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, orderBy)
                        // Offset & Limit
                        if (skip != null) putInt(ContentResolver.QUERY_ARG_OFFSET, skip)
                        if (take != null) putInt(ContentResolver.QUERY_ARG_LIMIT, take)
                    },
                    null
                )
            } else {
                val offset = if (skip != null) "OFFSET $skip" else ""
                val limit = if (take != null) "LIMIT $take" else ""

                imageCursor = this.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "$orderBy $offset $limit"
                )
            }

            return imageCursor
        }
    }

    private fun getVideoCursor(
        albumId: String,
        newest: Boolean,
        projection: Array<String>,
        skip: Int?,
        take: Int?
    ): Cursor? {
        this.context.run {
            val isSelection = albumId != allAlbumId
            val selection = if (isSelection) "${MediaStore.Video.Media.BUCKET_ID} = ?" else null
            val selectionArgs = if (isSelection) arrayOf(albumId) else null
            val orderBy = if (newest) {
                "${MediaStore.Video.Media.DATE_ADDED} DESC, ${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            } else {
                "${MediaStore.Video.Media.DATE_ADDED} ASC, ${MediaStore.Video.Media.DATE_MODIFIED} ASC"
            }

            val videoCursor: Cursor?

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    android.os.Bundle().apply {
                        // Selection
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                        // Sort
                        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, orderBy)
                        // Offset & Limit
                        if (skip != null) putInt(ContentResolver.QUERY_ARG_OFFSET, skip)
                        if (take != null) putInt(ContentResolver.QUERY_ARG_LIMIT, take)
                    },
                    null
                )
            } else {
                val offset = if (skip != null) "OFFSET $skip" else ""
                val limit = if (take != null) "LIMIT $take" else ""

                videoCursor = this.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "$orderBy $offset $limit"
                )
            }

            return videoCursor
        }
    }

    private fun getFile(mediumId: String, mediumType: String?, mimeType: String?): String? {
        return when (mediumType) {
            imageType -> {
                getImageFile(mediumId, mimeType = mimeType)
            }

            videoType -> {
                getVideoFile(mediumId)
            }

            else -> {
                getImageFile(mediumId, mimeType = mimeType) ?: getVideoFile(mediumId)
            }
        }
    }

    private fun getImageFile(mediumId: String, mimeType: String? = null): String? {
        return this.context.run {
            mimeType?.let {
                val type = this.contentResolver.getType(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediumId.toLong()
                    )
                )
                if (it != type) {
                    return@run cacheImage(mediumId, it)
                }
            }

            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA),
                "${MediaStore.Images.Media._ID} = ?",
                arrayOf(mediumId),
                null
            )

            imageCursor?.use { cursor ->
                if (cursor.moveToNext()) {
                    val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                    return@run cursor.getString(dataColumn)
                }
            }

            return@run null
        }
    }

    private fun getVideoFile(mediumId: String): String? {
        return this.context.run {
            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media.DATA),
                "${MediaStore.Video.Media._ID} = ?",
                arrayOf(mediumId),
                null
            )

            videoCursor?.use { cursor ->
                if (cursor.moveToNext()) {
                    val dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    return@run cursor.getString(dataColumn)
                }
            }

            return@run null
        }
    }

    private fun cacheImage(mediumId: String, mimeType: String): String? {
        val bitmap: Bitmap? = this.context.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(
                            this.contentResolver,
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediumId.toLong())
                        )
                    )
                } catch (e: Exception) {
                    null
                }
            } else {
                MediaStore.Images.Media.getBitmap(
                    this.contentResolver,
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediumId.toLong())
                )
            }
        }

        return bitmap?.let {
            val compressFormat: Bitmap.CompressFormat
            when (mimeType) {
                "image/jpeg" -> {
                    val path = File(getCachePath(), "$mediumId.jpeg")
                    val out = FileOutputStream(path)
                    compressFormat = Bitmap.CompressFormat.JPEG
                    it.compress(compressFormat, 100, out)
                    path.absolutePath
                }

                "image/png" -> {
                    val path = File(getCachePath(), "$mediumId.png")
                    val out = FileOutputStream(path)
                    compressFormat = Bitmap.CompressFormat.PNG
                    it.compress(compressFormat, 100, out)
                    path.absolutePath
                }

                "image/webp" -> {
                    val path = File(getCachePath(), "$mediumId.webp")
                    val out = FileOutputStream(path)
                    compressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        Bitmap.CompressFormat.WEBP
                    }
                    it.compress(compressFormat, 100, out)
                    path.absolutePath
                }

                else -> {
                    null
                }
            }
        }
    }

    private fun getImageMetadata(cursor: Cursor): Map<String, Any?> {
        val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val filenameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
        val titleColumn = cursor.getColumnIndex(MediaStore.Images.Media.TITLE)
        val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
        val sizeColumn = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
        val orientationColumn = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
        val mimeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
        val dateAddedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)

        val id = cursor.getLong(idColumn)
        val filename = cursor.getString(filenameColumn)
        val title = cursor.getString(titleColumn)
        val width = cursor.getLong(widthColumn)
        val height = cursor.getLong(heightColumn)
        val size = cursor.getLong(sizeColumn)
        val orientation = cursor.getLong(orientationColumn)
        val mimeType = cursor.getString(mimeColumn)
        var dateAdded: Long? = null
        if (cursor.getType(dateAddedColumn) == FIELD_TYPE_INTEGER) {
            dateAdded = cursor.getLong(dateAddedColumn) * 1000
        }
        var dateModified: Long? = null
        if (cursor.getType(dateModifiedColumn) == FIELD_TYPE_INTEGER) {
            dateModified = cursor.getLong(dateModifiedColumn) * 1000
        }

        return mapOf(
            "id" to id.toString(),
            "filename" to filename,
            "title" to title,
            "mediumType" to imageType,
            "width" to width,
            "height" to height,
            "size" to size,
            "orientation" to orientationDegree2Value(orientation),
            "mimeType" to mimeType,
            "creationDate" to dateAdded,
            "modifiedDate" to dateModified
        )
    }

    private fun getImageBriefMetadata(cursor: Cursor): Map<String, Any?> {
        val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
        val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
        val orientationColumn = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)
        val dateAddedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)

        val id = cursor.getLong(idColumn)
        val width = cursor.getLong(widthColumn)
        val height = cursor.getLong(heightColumn)
        val orientation = cursor.getLong(orientationColumn)
        var dateAdded: Long? = null
        if (cursor.getType(dateAddedColumn) == FIELD_TYPE_INTEGER) {
            dateAdded = cursor.getLong(dateAddedColumn) * 1000
        }
        var dateModified: Long? = null
        if (cursor.getType(dateModifiedColumn) == FIELD_TYPE_INTEGER) {
            dateModified = cursor.getLong(dateModifiedColumn) * 1000
        }

        return mapOf(
            "id" to id.toString(),
            "mediumType" to imageType,
            "width" to width,
            "height" to height,
            "orientation" to orientationDegree2Value(orientation),
            "creationDate" to dateAdded,
            "modifiedDate" to dateModified
        )
    }

    private fun getVideoMetadata(cursor: Cursor): Map<String, Any?> {
        val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
        val filenameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val titleColumn = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
        val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
        val sizeColumn = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
        val mimeColumn = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
        val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val dateAddedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

        val id = cursor.getLong(idColumn)
        val filename = cursor.getString(filenameColumn)
        val title = cursor.getString(titleColumn)
        val width = cursor.getLong(widthColumn)
        val height = cursor.getLong(heightColumn)
        val size = cursor.getLong(sizeColumn)
        val mimeType = cursor.getString(mimeColumn)
        val duration = cursor.getLong(durationColumn)
        var dateAdded: Long? = null
        if (cursor.getType(dateAddedColumn) == FIELD_TYPE_INTEGER) {
            dateAdded = cursor.getLong(dateAddedColumn) * 1000
        }
        var dateModified: Long? = null
        if (cursor.getType(dateModifiedColumn) == FIELD_TYPE_INTEGER) {
            dateModified = cursor.getLong(dateModifiedColumn) * 1000
        }

        return mapOf(
            "id" to id.toString(),
            "filename" to filename,
            "title" to title,
            "mediumType" to videoType,
            "width" to width,
            "height" to height,
            "size" to size,
            "mimeType" to mimeType,
            "duration" to duration,
            "creationDate" to dateAdded,
            "modifiedDate" to dateModified
        )
    }

    private fun getVideoBriefMetadata(cursor: Cursor): Map<String, Any?> {
        val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
        val widthColumn = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
        val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val dateAddedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)

        val id = cursor.getLong(idColumn)
        val width = cursor.getLong(widthColumn)
        val height = cursor.getLong(heightColumn)
        val duration = cursor.getLong(durationColumn)
        var dateAdded: Long? = null
        if (cursor.getType(dateAddedColumn) == FIELD_TYPE_INTEGER) {
            dateAdded = cursor.getLong(dateAddedColumn) * 1000
        }
        var dateModified: Long? = null
        if (cursor.getType(dateModifiedColumn) == FIELD_TYPE_INTEGER) {
            dateModified = cursor.getLong(dateModifiedColumn) * 1000
        }

        return mapOf(
            "id" to id.toString(),
            "mediumType" to videoType,
            "width" to width,
            "height" to height,
            "duration" to duration,
            "creationDate" to dateAdded,
            "modifiedDate" to dateModified
        )
    }

    private fun orientationDegree2Value(degree: Long): Int {
        return when (degree) {
            0L -> 1
            90L -> 8
            180L -> 3
            270L -> 6
            else -> 0
        }
    }

    private fun getCachePath(): File {
        return this.context.run {
            val cachePath = File(this.cacheDir, "photo_gallery")
            if (!cachePath.exists()) {
                cachePath.mkdirs()
            }
            return@run cachePath
        }
    }

    private fun deleteMedium(mediumId: String, mediumType: String?) {
        when (mediumType) {
            imageType -> {
                deleteImageMedium(mediumId)
            }

            videoType -> {
                deleteVideoMedium(mediumId)
            }

            else -> {
                deleteImageMedium(mediumId)
                deleteVideoMedium(mediumId)
            }
        }
    }


    private fun deleteImageMedium(mediumId: String) {
        this.context.run {
            val selection = "${MediaStore.Images.Media._ID} = ?"
            val selectionArgs = arrayOf(mediumId)
            val imageCursor = this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null,
                selection,
                selectionArgs,
                null
            )
            imageCursor?.use {
                if (it.count > 0) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        val pendingIntent = MediaStore.createTrashRequest(
                            this.contentResolver,
                            Collections.singleton(
                                ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    mediumId.toLong()
                                )
                            ),
                            true
                        )
                        activity?.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            0,
                            null,
                            0,
                            0,
                            0
                        )
                    } else {
                        try {
                            this.contentResolver.delete(
                                ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    mediumId.toLong()
                                ),
                                selection,
                                selectionArgs
                            )
                        } catch (e: SecurityException) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val securityException = e as? RecoverableSecurityException ?: throw e
                                val intentSender = securityException.userAction.actionIntent.intentSender
                                activity?.startIntentSenderForResult(
                                    intentSender,
                                    0,
                                    null,
                                    0,
                                    0,
                                    0
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun deleteVideoMedium(mediumId: String) {
        this.context.run {
            val selection = "${MediaStore.Video.Media._ID} = ?"
            val selectionArgs = arrayOf(mediumId)
            val videoCursor = this.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                null,
                selection,
                selectionArgs,
                null
            )
            videoCursor?.use {
                if (it.count > 0) {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                        val pendingIntent = MediaStore.createTrashRequest(
                            this.contentResolver,
                            Collections.singleton(
                                ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    mediumId.toLong()
                                )
                            ),
                            true
                        )
                        activity?.startIntentSenderForResult(
                            pendingIntent.intentSender,
                            0,
                            null,
                            0,
                            0,
                            0
                        )
                    } else {
                        try {
                            this.contentResolver.delete(
                                ContentUris.withAppendedId(
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                    mediumId.toLong()
                                ),
                                selection,
                                selectionArgs
                            )
                        } catch (e: SecurityException) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val securityException = e as? RecoverableSecurityException ?: throw e
                                val intentSender = securityException.userAction.actionIntent.intentSender
                                activity?.startIntentSenderForResult(
                                    intentSender,
                                    0,
                                    null,
                                    0,
                                    0,
                                    0
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun cleanCache() {
        val cachePath = getCachePath()
        cachePath.deleteRecursively()
    }
}
