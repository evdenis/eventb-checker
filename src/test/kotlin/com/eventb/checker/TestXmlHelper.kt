package com.eventb.checker

import org.w3c.dom.Document
import javax.xml.parsers.DocumentBuilderFactory

object TestXmlHelper {

    fun parseDoc(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(xml.byteInputStream())
    }
}
