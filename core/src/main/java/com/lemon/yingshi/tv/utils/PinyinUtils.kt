package com.lemon.yingshi.tv.utils

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * 拼音工具类
 * 用于中文转拼音，支持搜索功能
 */
object PinyinUtils {

    private val outputFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    /**
     * 获取字符串的拼音首字母缩写
     * 例如："刘德华" -> "ldh"
     */
    fun getPinyinInitials(source: String): String {
        return source.mapNotNull { char ->
            val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, outputFormat)
            if (pinyinArray.isNotEmpty()) {
                pinyinArray[0].first().toString()
            } else {
                char.toString()
            }
        }.joinToString("")
    }

    /**
     * 获取字符串的完整拼音
     * 例如："刘德华" -> "liudehua"
     */
    fun getPinyin(source: String): String {
        return source.mapNotNull { char ->
            val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, outputFormat)
            if (pinyinArray.isNotEmpty()) {
                pinyinArray[0]
            } else {
                char.toString()
            }
        }.joinToString("")
    }

    /**
     * 获取字符串的拼音（带空格分隔）
     * 例如："刘德华" -> "liu de hua"
     */
    fun getPinyinWithSpace(source: String): String {
        return source.mapNotNull { char ->
            val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, outputFormat)
            if (pinyinArray.isNotEmpty()) {
                pinyinArray[0]
            } else {
                char.toString()
            }
        }.joinToString(" ")
    }

    /**
     * 检查是否包含中文
     */
    fun containsChinese(source: String): Boolean {
        return source.any { char ->
            Character.UnicodeBlock.of(char.code) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
        }
    }

    /**
     * 将中文字符转换为搜索友好的格式
     * 例如："刘德华演唱会" -> ["刘德华", "liudehua", "ldh", "演唱会"]
     */
    fun toSearchTerms(source: String): List<String> {
        val terms = mutableListOf(source)
        
        if (containsChinese(source)) {
            terms.add(getPinyin(source))
            terms.add(getPinyinInitials(source))
        }
        
        return terms
    }
}

/**
 * 扩展函数：获取字符串的拼音首字母
 */
fun String.toPinyinInitials(): String = PinyinUtils.getPinyinInitials(this)

/**
 * 扩展函数：获取字符串的拼音
 */
fun String.toPinyin(): String = PinyinUtils.getPinyin(this)
