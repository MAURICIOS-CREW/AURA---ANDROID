package com.mexadev.aura.ui.community

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PostModel(
    val id: Long = 0,
    val authorName: String,
    val authorAvatar: String? = null,
    val content: String,
    val imageResId: Int? = null,
    val timestamp: String,
    var likes: Int,
    var comments: Int,
    var isLiked: Boolean = false
)

data class CommentModel(
    val id: Long = 0,
    val postId: Long,
    val authorName: String,
    val content: String,
    val timestamp: String,
    val isMe: Boolean = false
)

class CommunityDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "AuraCommunity.db"

        // Table Posts
        const val TABLE_POSTS = "posts"
        const val COLUMN_ID = "_id"
        const val COLUMN_AUTHOR = "author"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_IMAGE = "image"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_LIKES = "likes"
        const val COLUMN_COMMENTS = "comments"
        const val COLUMN_IS_LIKED = "is_liked"

        // Table Comments
        const val TABLE_COMMENTS = "comments"
        const val COLUMN_COMMENT_ID = "_id"
        const val COLUMN_COMMENT_POST_ID = "post_id"
        const val COLUMN_COMMENT_AUTHOR = "author"
        const val COLUMN_COMMENT_CONTENT = "content"
        const val COLUMN_COMMENT_TIMESTAMP = "timestamp"
        const val COLUMN_COMMENT_IS_ME = "is_me"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createPostsTable = ("CREATE TABLE " + TABLE_POSTS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_AUTHOR + " TEXT,"
                + COLUMN_CONTENT + " TEXT,"
                + COLUMN_IMAGE + " INTEGER,"
                + COLUMN_TIMESTAMP + " TEXT,"
                + COLUMN_LIKES + " INTEGER,"
                + COLUMN_COMMENTS + " INTEGER,"
                + COLUMN_IS_LIKED + " INTEGER" + ")")
        db.execSQL(createPostsTable)

        val createCommentsTable = ("CREATE TABLE " + TABLE_COMMENTS + "("
                + COLUMN_COMMENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_COMMENT_POST_ID + " INTEGER,"
                + COLUMN_COMMENT_AUTHOR + " TEXT,"
                + COLUMN_COMMENT_CONTENT + " TEXT,"
                + COLUMN_COMMENT_TIMESTAMP + " TEXT,"
                + COLUMN_COMMENT_IS_ME + " INTEGER" + ")")
        db.execSQL(createCommentsTable)

        // Insert initial dummy posts
        val now = System.currentTimeMillis()
        val id1 = insertDummyPost(db, "Administración AURA", "¡Bienvenidos a la nueva sección de Comunidad! Aquí podremos compartir avisos, recomendaciones y mantenernos en contacto.", null, (now - 2 * 3600 * 1000L).toString(), 15, 3)
        val id2 = insertDummyPost(db, "Carlos Gómez", "Hola vecinos, ¿alguien sabe a qué hora pasa el de la basura los miércoles? Gracias.", null, (now - 5 * 3600 * 1000L).toString(), 2, 1)
        val id3 = insertDummyPost(db, "Mantenimiento", "Se ha finalizado la limpieza profunda de la alberca. Ya pueden hacer uso de las instalaciones con normalidad.", null, (now - 24 * 3600 * 1000L).toString(), 34, 2)

        // Insert initial comments
        if (id1 != -1L) {
            insertDummyComment(db, id1, "María R.", "Excelente iniciativa, muchas gracias por mantener el espacio activo.", (now - 1 * 3600 * 1000L).toString(), false)
            insertDummyComment(db, id1, "Tú", "¡Super bien! Me alegra ver que tenemos esta herramienta.", (now - 30 * 60 * 1000L).toString(), true)
            insertDummyComment(db, id1, "Admin AURA", "Quedamos al pendiente para cualquier duda o sugerencia.", (now - 15 * 60 * 1000L).toString(), false)
        }
        if (id2 != -1L) {
            insertDummyComment(db, id2, "Roberto L.", "Pasa los miércoles y sábados aproximadamente a las 8:00 AM.", (now - 4 * 3600 * 1000L).toString(), false)
        }
        if (id3 != -1L) {
            insertDummyComment(db, id3, "Ana P.", "¡Genial! Muchas gracias por el mantenimiento.", (now - 20 * 3600 * 1000L).toString(), false)
            insertDummyComment(db, id3, "Tú", "Perfecto, iré por la tarde.", (now - 18 * 3600 * 1000L).toString(), true)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun insertDummyPost(db: SQLiteDatabase, author: String, content: String, image: Int?, time: String, likes: Int, comments: Int): Long {
        val values = ContentValues().apply {
            put(COLUMN_AUTHOR, author)
            put(COLUMN_CONTENT, content)
            put(COLUMN_IMAGE, image)
            put(COLUMN_TIMESTAMP, time)
            put(COLUMN_LIKES, likes)
            put(COLUMN_COMMENTS, comments)
            put(COLUMN_IS_LIKED, 0)
        }
        return db.insert(TABLE_POSTS, null, values)
    }

    private fun insertDummyComment(db: SQLiteDatabase, postId: Long, author: String, content: String, time: String, isMe: Boolean): Long {
        val values = ContentValues().apply {
            put(COLUMN_COMMENT_POST_ID, postId)
            put(COLUMN_COMMENT_AUTHOR, author)
            put(COLUMN_COMMENT_CONTENT, content)
            put(COLUMN_COMMENT_TIMESTAMP, time)
            put(COLUMN_COMMENT_IS_ME, if (isMe) 1 else 0)
        }
        return db.insert(TABLE_COMMENTS, null, values)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_POSTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_COMMENTS")
        onCreate(db)
    }

    fun getAllPosts(): List<PostModel> {
        val postsList = mutableListOf<PostModel>()
        val selectQuery = "SELECT * FROM $TABLE_POSTS ORDER BY $COLUMN_ID DESC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val post = PostModel(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    authorName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUTHOR)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                    imageResId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IMAGE)).takeIf { it != 0 },
                    timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    likes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LIKES)),
                    comments = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COMMENTS)),
                    isLiked = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_LIKED)) == 1
                )
                postsList.add(post)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return postsList
    }

    fun getPostById(postId: Long): PostModel? {
        val db = this.readableDatabase
        val cursor = db.query(TABLE_POSTS, null, "$COLUMN_ID = ?", arrayOf(postId.toString()), null, null, null)
        var post: PostModel? = null
        if (cursor.moveToFirst()) {
            post = PostModel(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                authorName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUTHOR)),
                content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                imageResId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IMAGE)).takeIf { it != 0 },
                timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                likes = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_LIKES)),
                comments = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COMMENTS)),
                isLiked = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_LIKED)) == 1
            )
        }
        cursor.close()
        return post
    }

    fun addPost(post: PostModel): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_AUTHOR, post.authorName)
            put(COLUMN_CONTENT, post.content)
            put(COLUMN_IMAGE, post.imageResId)
            put(COLUMN_TIMESTAMP, post.timestamp)
            put(COLUMN_LIKES, post.likes)
            put(COLUMN_COMMENTS, post.comments)
            put(COLUMN_IS_LIKED, if (post.isLiked) 1 else 0)
        }
        return db.insert(TABLE_POSTS, null, values)
    }

    fun toggleLike(postId: Long): PostModel? {
        val currentPost = getPostById(postId) ?: return null
        val newIsLiked = !currentPost.isLiked
        val newLikes = if (newIsLiked) currentPost.likes + 1 else (currentPost.likes - 1).coerceAtLeast(0)

        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_LIKES, newLikes)
            put(COLUMN_IS_LIKED, if (newIsLiked) 1 else 0)
        }
        db.update(TABLE_POSTS, values, "$COLUMN_ID = ?", arrayOf(postId.toString()))

        return currentPost.copy(likes = newLikes, isLiked = newIsLiked)
    }

    fun getCommentsForPost(postId: Long): List<CommentModel> {
        val commentsList = mutableListOf<CommentModel>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_COMMENTS,
            null,
            "$COLUMN_COMMENT_POST_ID = ?",
            arrayOf(postId.toString()),
            null, null,
            "$COLUMN_COMMENT_ID ASC"
        )

        if (cursor.moveToFirst()) {
            do {
                val comment = CommentModel(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMMENT_ID)),
                    postId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMMENT_POST_ID)),
                    authorName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMMENT_AUTHOR)),
                    content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMMENT_CONTENT)),
                    timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMMENT_TIMESTAMP)),
                    isMe = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_COMMENT_IS_ME)) == 1
                )
                commentsList.add(comment)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return commentsList
    }

    fun addComment(comment: CommentModel): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_COMMENT_POST_ID, comment.postId)
            put(COLUMN_COMMENT_AUTHOR, comment.authorName)
            put(COLUMN_COMMENT_CONTENT, comment.content)
            put(COLUMN_COMMENT_TIMESTAMP, comment.timestamp)
            put(COLUMN_COMMENT_IS_ME, if (comment.isMe) 1 else 0)
        }
        val commentId = db.insert(TABLE_COMMENTS, null, values)

        if (commentId != -1L) {
            // Update comments count in posts table
            db.execSQL("UPDATE $TABLE_POSTS SET $COLUMN_COMMENTS = $COLUMN_COMMENTS + 1 WHERE $COLUMN_ID = ?", arrayOf(comment.postId))
        }

        return commentId
    }
}
