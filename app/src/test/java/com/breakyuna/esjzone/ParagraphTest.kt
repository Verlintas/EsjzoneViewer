package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphTest {

    @Test
    fun test() {
        val element = Jsoup.parse("<div><p><span style=\"font-size:18px;\">出生於貴族家庭的凡，在兩歲時突然想起自己是轉世者。</span><br><br><span style=\"font-size:18px;\">凡表現出了不像幼兒的行為，一度被傳言成神童。</span><br><br><span style=\"font-size:18px;\">然而，在進行魔術適性鑑定儀式時，結果顯示他有制造物品生產系魔法的能力，但卻被譏笑為無用之人，</span><br><span style=\"font-size:18px;\">最終被趕出侯爵家。</span><br><span style=\"font-size:18px;\">在流放到村莊後，凡面臨各種困難，但仍然堅持發展和防禦該村莊，最终成長為一個巨大城市。</span><br>---------------------------------<br>文章是用GPT3.5為主翻譯，加一點潤色為輔。算是測試一下AI。最近開通4.0API了，覺得，句子有比較順。<br>---------------------------------<br>最近很喜歡看大家在下面留言吐糟，雖然作者內文本來很多地方就不合邏輯，所以吐糟的好啊，很好笑啊。<br>--------------------------------<br>潤色的基準，60%都以原文原意下去翻譯，20%會照原文修改句子通順度，10%如果用原文去看跟本看不懂，只能用接近的意思重新編排，5%會補些重點（例如這句話誰講的啊，誰做出了什麼事啊），最後5%就人名或特定名詞之類的。<br>--------------------------------<br>感謝Daytona 、Abay 、TYVM 、siumonmon 、<a href=\"https://www.esjzone.me/my/profile?uid=53938\" target=\"_blank\">星界</a>、<a href=\"https://www.esjzone.me/my/profile?uid=81006\" target=\"_blank\">歐邁尼斯</a>、pwo、yiyuandian 、路人己、<a href=\"https://www.esjzone.me/my/profile?uid=86992\" target=\"_blank\">chondrite</a> 、<a href=\"https://www.esjzone.me/my/profile?uid=151915\" target=\"_blank\">3.14159265358979323846264338</a>、<a href=\"https://www.esjzone.me/my/profile?uid=104980\" target=\"_blank\">largejj</a> 、AIR、BleakBoy，腦洞 、jason38 、流浪者、skr、hjb4081 大大們常常幫小弟回報錯別字。感謝你們的付出。<br>--------------------------------<br>000C-譯者的話（翻譯工具），有我用工具的心得，可以参考看看。<br>-------------------------------<br>如果你是用android系統在看線上小說的話，推薦用靈魂瀏覽器看喔，google市集裡面就有了，它唯一特別的地方，就是它會記錄你看到那裡，你下次在打開時，它會停在你上一次看到的地方。很好用喔。<br>------------------------------<br>除了錯字回報外，如果那裡讀起來卡卡的，也就是你讀起來是看的懂，但是就是卡卡的，也是歡迎用錯字回報的方式回報喔。<br>------------------------------<br>啊，剛發現，如果是主角的父親︰杰爾帕（如果用網站的繁簡轉換或是一些繁簡轉換的程式，會變成傑爾帕......），這，只能請大家自己腦補一下了。<br>-----------------------------<br><br><span style=\"font-size:18px;\">給大家小禮物（真的只是機翻喔）︰<span style=\"color:rgb(44, 130, 201);\"><a href=\"https://books.fishhawk.top/\" target=\"_blank\">機翻網</a></span></span><br><br><span style=\"font-size:18px;\"><span style=\"color:rgb(44, 130, 201);\">--------------------</span></span><br><span style=\"font-size: 24px; color: rgb(209, 72, 65);\">2023 0907</span></p></div>").testResolve()
        println(element)
        println(element.children().toList())
        analyseDescription(element)
            .components.forEach {
                println(it)
                if (it is TextComponent) {
                    println(it.getExtras())
                }
            }
    }
    @Test
    fun nestedImageInAnchor_isPreserved() {
        val element = Jsoup.parse("<div><p><a href=\"https://example.com/view\"><img src=\"https://example.com/test.jpg\" alt=\"pic\"></a></p></div>").testResolve()
        val components = analyseComponents(element)
        assertEquals(1, components.size)
        assertTrue(components.first() is ImageComponent)
        assertEquals("https://example.com/test.jpg", (components.first() as ImageComponent).url)
    }

    @Test
    fun mixedTextAndImage_preservesBothInOrder() {
        val element = Jsoup.parse("<div><p>前缀文字<img src=\"https://example.com/mid.jpg\">后缀文字</p></div>").testResolve()
        val components = analyseComponents(element)
        assertEquals(3, components.size)
        assertTrue(components[0] is TextComponent)
        assertEquals("前缀文字", (components[0] as TextComponent).text)
        assertTrue(components[1] is ImageComponent)
        assertEquals("https://example.com/mid.jpg", (components[1] as ImageComponent).url)
        assertTrue(components[2] is TextComponent)
        assertEquals("后缀文字", (components[2] as TextComponent).text)
    }

    @Test
    fun multipleImagesInSameParagraph_areAllExtracted() {
        val element = Jsoup.parse("<div><p><img src=\"https://example.com/1.jpg\"><img src=\"https://example.com/2.jpg\"></p></div>").testResolve()
        val components = analyseComponents(element)
        assertEquals(2, components.size)
        assertTrue(components[0] is ImageComponent)
        assertEquals("https://example.com/1.jpg", (components[0] as ImageComponent).url)
        assertTrue(components[1] is ImageComponent)
        assertEquals("https://example.com/2.jpg", (components[1] as ImageComponent).url)
    }

    @Test
    fun directImageChild_isExtracted() {
        val element = Jsoup.parse("<div><img src=\"https://example.com/direct.jpg\"></div>").testResolve()
        val components = analyseComponents(element)
        assertEquals(1, components.size)
        assertTrue(components.first() is ImageComponent)
        assertEquals("https://example.com/direct.jpg", (components.first() as ImageComponent).url)
    }

    @Test
    fun sectionWithNestedParagraphs_extractsAllParagraphsSeparately() {
        val element = Jsoup.parse(
            "<div><section><p>段落一</p><p>段落二</p><p>段落三</p></section></div>"
        ).testResolve()
        val components = analyseComponents(element)
        assertEquals(3, components.size)
        assertTrue(components.all { it is TextComponent })
        assertEquals("段落一", (components[0] as TextComponent).text)
        assertEquals("段落二", (components[1] as TextComponent).text)
        assertEquals("段落三", (components[2] as TextComponent).text)
    }

    @Test
    fun nestedContainersAndLooseText_preservesAllParagraphsInOrder() {
        val element = Jsoup.parse(
            "<div><p><br></p><section><p>前文</p><div><p>嵌套内容</p></div><p>后文</p></section></div>"
        ).testResolve()
        val components = analyseComponents(element)
        assertEquals(4, components.size)
        assertEquals("\n", (components[0] as TextComponent).text)
        assertEquals("前文", (components[1] as TextComponent).text)
        assertEquals("嵌套内容", (components[2] as TextComponent).text)
        assertEquals("后文", (components[3] as TextComponent).text)
    }

    @Test
    fun looseTextAlongsideBlockElements_isPreserved() {
        val element = Jsoup.parse(
            "<div>提示信息<br>请注意<p>第一章</p><p>第二章</p></div>"
        ).testResolve()
        val components = analyseComponents(element)
        assertEquals(3, components.size)
        assertTrue((components[0] as TextComponent).text.contains("提示信息"))
        assertEquals("第一章", (components[1] as TextComponent).text)
        assertEquals("第二章", (components[2] as TextComponent).text)
    }
}

private fun Document.testResolve(): Element {
    return this.getElementsByTag("html")[0].getElementsByTag("body")[0].getElementsByTag("div")[0]
}