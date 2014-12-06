package org.ferrit.core.util

import org.allenai.common.testkit.UnitSpec

import org.ferrit.core.util.TextFormatter._


class TestTextFormatter extends UnitSpec {

  behavior of "TextFormatter"

  it should "crop a string" in {
    lcrop("spaced out", 0) should equal ("")
    lcrop("spaced out", 6) should equal ("spaced")
    lcrop("spaced out", 10) should equal ("spaced out")
    lcrop("spaced out", 100) should equal ("spaced out")
    rcrop("spaced out", 3) should equal ("out")
  }

  it should "create a line of characters" in {
    line("-", 0) should equal ("")
    line("-", 1) should equal ("-")
    line("-", 10) should equal ("----------")
    line("+-", 9) should equal ("+-+-+-+-+")
  }

  it should "produce a left aligned cell" in {
    lcell(2.toString + " ",  2, ".") should equal ("2 ")
    lcell(2.toString + " ", 10, ".") should equal ("2 ........")
    lcell(200.toString + " ",  3, ".") should equal ("200")
  }

  it should "produce a right aligned cell" in {
    rcell(" " + 2.toString,  2, ".") should equal (" 2")
    rcell(" " + 2.toString,  3, ".") should equal (". 2")
    rcell(" " + 2.toString, 10, ".") should equal ("........ 2")
    rcell(" " + 200.toString,  3, ".") should equal ("200")
  }

  it should "format elapsed time" in {

    val Millis = 1
    val Second = Millis * 1000
    val Minute = Second * 60
    val Hour = Minute * 60
    val Day = Hour * 24

    formatElapsedTime(Second) should equal ("1s")
    formatElapsedTime(Second + Millis) should equal ("1s")
    formatElapsedTime(Minute) should equal ("1m")
    formatElapsedTime(Minute + 125) should equal ("1m") // doesn't show ms
    formatElapsedTime(Minute + Second) should equal ("1m 1s")
    formatElapsedTime(Hour) should equal ("1h")
    formatElapsedTime(Hour + Second) should equal ("1h 1s")
    formatElapsedTime(Hour + Minute) should equal ("1h 1m")
    formatElapsedTime(Hour + Minute + Second) should equal ("1h 1m 1s")
    formatElapsedTime(Day) should equal ("1d")
    formatElapsedTime(Day + Second) should equal ("1d 1s")
    formatElapsedTime(Day + Minute + Second) should equal ("1d 1m 1s")
    formatElapsedTime(Day + Hour + Minute + Second) should equal ("1d 1h 1m 1s")
    formatElapsedTime(Second*9 + Millis*752) should equal ("9s") // should probably round up
    formatElapsedTime(Minute*5 + Second*59) should equal ("5m 59s")
    formatElapsedTime(Day*13 + Hour*5 + Minute*42 + Second*23) should equal ("13d 5h 42m 23s")

  }

}
