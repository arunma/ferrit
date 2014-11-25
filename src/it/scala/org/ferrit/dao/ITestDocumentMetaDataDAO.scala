package org.ferrit.dao

import org.ferrit.core.model.DocumentMetaData
import org.joda.time.DateTime
import org.scalatest.matchers.ShouldMatchers


class ITestDocumentMetaDataDAO extends AbstractDAOTest with ShouldMatchers {
  
  behavior of "DocumentMetaDataDAO"

  val docMetaDao = daoFactory.documentMetaDataDao

  it should "insert and read back a meta document row" in {

    val crawlerId = "1234"
    val jobId = "4321"
    val uri = "http://site.net/page1"

    val docMeta = DocumentMetaData(
      crawlerId, 
      jobId,
      uri,
      "text/html; charset=UTF-8",
      234234,
      1,
      new DateTime,
      "200"
    )

    docMetaDao.insert(docMeta)

    docMetaDao.find(jobId, uri) match {
      case Some(docMeta2) => docMeta2 should equal (docMeta)
      case None => fail(s"DocumentMetaData not found")
    }

  }

  it should "read many meta documents" in {
    
    val maxDocs  =10
    val jobId = "4321"

    val docs:Seq[DocumentMetaData] = (1 to maxDocs).map(i => {  
      DocumentMetaData(
        "1234", 
        jobId,
        s"http://site.net/page$i",
        "text/html; charset=UTF-8",
        234234,
        i,
        new DateTime,
        "200"
      )      
    })

    docs.foreach(docMetaDao.insert)

    docMetaDao.find(jobId) match {
      case Nil => fail(s"Did not find $maxDocs meta documents")
      case `docs` => docs.size should equal(maxDocs)
    }

  }

}