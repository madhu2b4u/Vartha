package org.vartha.news.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vartha.news.model.ParsedNews
import org.vartha.news.repository.NewsRepository


@RestController
@RequestMapping("/api")
class NewsController (@Autowired private val newsRepository: NewsRepository) {
    @get:GetMapping
    val finaAll: MutableList<ParsedNews> get() = newsRepository.findAll()
}
