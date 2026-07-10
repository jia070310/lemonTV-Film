package com.lemon.yingshi.tv.data.scraper

import com.lemon.yingshi.tv.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryFolderTypeHintsTest {

    @Test
    fun directTypeFolder() {
        assertEquals(MediaType.MOVIE, LibraryFolderTypeHints.detectMediaTypeFromPath("电影/1.MP4"))
        assertEquals(MediaType.TV_SHOW, LibraryFolderTypeHints.detectMediaTypeFromPath("电视剧/4-1080p.mkv"))
        assertEquals(MediaType.VARIETY, LibraryFolderTypeHints.detectMediaTypeFromPath("综艺/1 出嫁.mkv"))
        assertEquals(MediaType.DOCUMENTARY, LibraryFolderTypeHints.detectMediaTypeFromPath("纪录片/S01E10.MP4"))
        assertEquals(MediaType.CONCERT, LibraryFolderTypeHints.detectMediaTypeFromPath("演唱会/1.MP4"))
        assertEquals(MediaType.ANIME, LibraryFolderTypeHints.detectMediaTypeFromPath("动漫/4.MP4"))
    }

    @Test
    fun nestedPath_nearestWins() {
        assertEquals(
            MediaType.TV_SHOW,
            LibraryFolderTypeHints.detectMediaTypeFromPath("媒体库/电视剧/某剧/S01E01.mkv")
        )
        assertEquals(
            MediaType.VARIETY,
            LibraryFolderTypeHints.detectMediaTypeFromPath("媒体库/综艺/子目录/20240101.mkv")
        )
    }

    @Test
    fun noMatch() {
        assertNull(LibraryFolderTypeHints.detectMediaTypeFromPath("随便/无名/1.mkv"))
    }

    @Test
    fun englishFolderNames() {
        assertEquals(MediaType.MOVIE, LibraryFolderTypeHints.detectMediaTypeFromPath("Movies/foo.mp4"))
        assertEquals(MediaType.ANIME, LibraryFolderTypeHints.detectMediaTypeFromPath("Anime/x.mkv"))
    }
}
