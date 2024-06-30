package org.vartha.news.model

import com.fasterxml.jackson.annotation.JsonProperty
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Component

@Component
@Document("parsednews")
data class ParsedNews(
    var id: String = ObjectId.get().toHexString(),
    var loc: String = "",
    var source: String = "",
    var publicationDate: String = "",
    var title: String = "",
    var images: List<String>?,
    var caption: String? = "",
    var category: String = "",
    var summary: String = ""
)

data class UpdateRequest( @JsonProperty("summary") var summary: String = "")