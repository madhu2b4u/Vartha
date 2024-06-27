package org.vartha.news.repository

import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import org.vartha.news.model.ParsedNews

@Repository
interface NewsRepository : MongoRepository<ParsedNews, Int> {

    fun getParsedNewsItem(id : Int): ParsedNews
}
