package org.ferrit.core.crawler

import org.allenai.common.testkit.UnitSpec

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers

class TestDefaultStopRule extends UnitSpec {

  val stopRule = new DefaultStopRule

  it should "not report failure until seeds are converted to URI fetches" in {

    stopRule.isFailedCrawl(
          totalSeeds = 1,
          maxFailPercent = 0.1,
          fetchAttempts = 1,
          fetchFails = 0) should equal(false)

    stopRule.isFailedCrawl(
          totalSeeds = 10,
          maxFailPercent = 0.1,
          fetchAttempts = 10,
          fetchFails = 10) should equal(false)

 }

 it should "not report failure until threshold reached" in {

    stopRule.isFailedCrawl(
          totalSeeds = 1,
          maxFailPercent = 0.6,
          fetchAttempts = 2,
          fetchFails = 1) should equal(false)

    stopRule.isFailedCrawl(
          totalSeeds = 1,
          maxFailPercent = 0.5,
          fetchAttempts = 10,
          fetchFails = 4) should equal(false)

 }

 it should "stop with failure if there are too many failed fetches" in {

    stopRule.isFailedCrawl(
          totalSeeds = 1,
          maxFailPercent = 0.1,
          fetchAttempts = 1000,
          fetchFails = 100) should equal(true)

    stopRule.isFailedCrawl(
          totalSeeds = 1,
          maxFailPercent = 0.2,
          fetchAttempts = 1000,
          fetchFails = 200) should equal(true)

    stopRule.isFailedCrawl(
          totalSeeds = 1,
          maxFailPercent = 0.2,
          fetchAttempts = 10000,
          fetchFails = 2000) should equal(true)

  }

}
