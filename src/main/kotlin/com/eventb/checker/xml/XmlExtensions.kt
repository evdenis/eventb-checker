package com.eventb.checker.xml

import com.eventb.checker.validation.ValidationError
import com.eventb.checker.validation.ValidationSeverity
import org.w3c.dom.Element

internal fun Element.childElements(): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node is Element) result.add(node)
    }
    return result
}

internal fun Element.getAttrOrError(
    attrName: String,
    filePath: String,
    errors: MutableList<ValidationError>
): String {
    val value = getAttribute(attrName)
    if (value.isEmpty()) {
        errors.add(
            ValidationError(
                filePath = filePath,
                severity = ValidationSeverity.WARNING,
                message = "Element '${tagName}' missing required attribute '$attrName'",
                element = getAttribute(XmlConstants.ATTR_LABEL).ifEmpty {
                    getAttribute(XmlConstants.ATTR_NAME)
                }
            )
        )
    }
    return value
}
