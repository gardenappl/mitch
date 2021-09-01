package ua.gardenapple.itchupdater

import junit.framework.TestCase

fun assertTrue(nullableBoolean: Boolean?) = TestCase.assertEquals(nullableBoolean, true)
fun assertFalse(nullableBoolean: Boolean?) = TestCase.assertEquals(nullableBoolean, false)