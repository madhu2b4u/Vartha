package org.vartha.news.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.stereotype.Component

@Component
@Document("parsednews")
data class ParsedNewsItem (
    @Id
    private var id: Long = 0,
    @NotBlank
    @Size(max = 100)
    @Indexed(unique = true)
    private var loc: String,
    var source: String,
    var publicationDate: String,
    var title: String,
    var images: List<String>?,
    var caption: String?
)