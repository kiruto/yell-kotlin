package com.exyui.thor.core.ctrl

import com.exyui.thor.*
import com.exyui.thor.core.*
import com.exyui.thor.core.cache.ThorCache
import com.exyui.thor.core.cache.ThorSession
import com.exyui.thor.core.database.Comment
import com.exyui.thor.core.database.Thread
import com.exyui.thor.core.plugin.Bus
import com.exyui.thor.core.plugin.COMMENT
import com.exyui.thor.core.plugin.LIFE.*
import rx.Observable
import org.apache.commons.lang3.StringEscapeUtils.escapeHtml4 as esc

/**
 * Created by yuriel on 1/15/17.
 */
object Controller {

    private val host = if (DEBUG) listOf(HOST_DEBUG) else HOST_RELEASE
    private val origin = if (DEBUG) listOf(ORIGIN_DEBUG) else ORIGIN_RELEASE

    val info = mapOf(
            Pair("host", host),
            Pair("origin", origin),
            Pair("version", PACKAGE_VERSION),
            Pair("moderation", MODERATED)
    )

    @Throws(ThorBadRequest::class)
    fun insertComment(uri: String,
                      title: String,
                      author: String? = null,
                      email: String? = null,
                      website: String? = null,
                      text: String,
                      remoteAddr: String,
                      parent: Int? = null): Pair<Int, Comment> {
        val c = Comment.create(
                author = esc(author),
                parent = parent,
                email = esc(email),
                website = urlFor(website),
                text = esc(text),
                mode = if (MODERATED) 2 else 1,
                remoteAddr = remoteAddr.anonymize()
        )
        val v = c.verify()
        if (!v.valid)
            throw ThorBadRequest(v.reason)

        val thread = if (uri !in Thread) {
            // todo: check title and uri by request
            val t = Thread.new(uri, title)
            Bus.p(COMMENT.NEW, NEW_THREAD, t)
            t
        } else {
            Thread[uri]
        }

        Bus.p(COMMENT.NEW, BEFORE_SAVE, thread, c)
        val rv = c.insert(uri)
        Bus.p(COMMENT.NEW, AFTER_SAVE, rv.second)
        return rv
    }

    @Throws(ThorNotFound::class)
    fun viewComment(id: Int) = Comment[id] ?: throw ThorNotFound("comment($id)")

    private fun urlFor(url: String?): String? {
        url?: return null
        if (url.startsWith("https://", true) || url.startsWith("http://", true)) return url
        return "http://$url"
    }

    fun deleteComment(id: Int) {
        Comment.delete(id)
    }

    fun editComment(id: Int, text: String? = null, author: String? = null, website: String? = null): Comment {
        val result = Comment.update(id, text, author, website)
        return result
    }

    @Throws(ThorBadRequest::class)
    fun fetch(uri: String, after: Double = .0, parent: Int? = -1, limit: Int? = null, plain: Int? = 0, nestedLimit: Int? = null) {
        val rootId = parent
        val shouldPlain = plain == 0
        val replyCounts = Comment.replyCount(uri, after = after)
        val rootList = Comment.fetch(uri, 5, after, parent, limit = limit)
        rootList.isEmpty.subscribe { if (it) throw ThorNotFound(uri) }
        rootId?.let {
            if (it !in replyCounts) {
                replyCounts[it] = 0
            }
        }
        val totalReplies = if (rootId != null) replyCounts[rootId]?: 0 else 0
        val comments = processFetchedList(rootList, shouldPlain)
        val rv = FetchResult(rootId, totalReplies, totalReplies - rootList.count().toBlocking().single(), comments)

    }

    private fun processFetchedList(c: Observable<Comment>, plain: Boolean = false): List<Comment> {
        TODO()
//        c.map {
////            val key = it.email?: it.remoteAddr
////            val value = ThorCache["hash"][key]
//            var session = ThorSession[it.id!!]
//            if (session == null) {
//                ThorSession.save(it.id, it.remoteAddr, it.email)
//                session = ThorSession[it.id]
//            }
//
//        }
    }

    fun like(id: Int, remoteAddr: String) = Comment.vote(true, id, remoteAddr.anonymize())
    fun dislike(id: Int, remoteAddr: String) = Comment.vote(false, id, remoteAddr.anonymize())
    fun counts(vararg uri: String): IntArray =  Comment.count(*uri)
    fun count(uri: String): Int = counts(uri)[0]

}