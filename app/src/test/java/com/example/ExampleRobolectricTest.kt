package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ThreatEvaluationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FocusSense", appName)
  }

  @Test
  fun `threat engine detects stranger risk`() {
    val engine = ThreatEvaluationEngine()
    val result = engine.evaluateLayer2MobileBertML(
      appName = "Discord",
      contentTitle = "Direct Message",
      extractedText = "Hey are you alone? Don't tell your mom or dad, let's meet up at the skatepark behind school at 6pm"
    )
    assertTrue("Should be flagged", result.isFlagged)
    assertTrue("Should contain Stranger Risk", result.threatCategory.contains("Stranger Risk"))
    assertTrue("Confidence should be high", result.confidenceScore >= 0.70f)
  }

  @Test
  fun `threat engine marks educational content as safe`() {
    val engine = ThreatEvaluationEngine()
    val result = engine.evaluateLayer2MobileBertML(
      appName = "YouTube",
      contentTitle = "Khan Academy",
      extractedText = "Introduction to cellular biology and photosynthesis in plant cells"
    )
    assertFalse("Should not be flagged", result.isFlagged)
    assertEquals("Safe", result.threatCategory)
  }

  @Test
  fun `schedule rule blocks restricted package during active hours`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.FocusSenseRepository(context)

    // Add an all-day rule restricting Chrome
    val rule = com.example.data.model.ScheduleRuleEntity(
      ruleId = "test-curfew-1",
      childId = "child-default",
      ruleName = "Study Focus",
      category = "Homework",
      startTime = "00:00",
      endTime = "23:59",
      dayOfWeek = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
      restrictedPackages = "com.android.chrome",
      isActive = true
    )
    repository.addScheduleRule(rule)

    val restriction = repository.checkAppRestriction("child-default", "com.android.chrome")
    assertTrue("com.android.chrome should be blocked during active curfew", restriction.isBlocked)
    assertEquals("Study Focus", restriction.ruleName)

    val allowedRestriction = repository.checkAppRestriction("child-default", "com.example.unrestricted")
    assertFalse("Unrestricted package should not be blocked", allowedRestriction.isBlocked)
  }

  @Test
  fun `instant app lock blocks app immediately`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.FocusSenseRepository(context)

    repository.toggleAppBlock("child-default", "com.google.android.youtube", true)

    assertTrue("Should be blocked by instant lock", repository.isPackageBlockedSync("com.google.android.youtube"))
    val check = repository.checkAppRestriction("child-default", "com.google.android.youtube")
    assertTrue("checkAppRestriction should return blocked", check.isBlocked)

    repository.toggleAppBlock("child-default", "com.google.android.youtube", false)
    assertFalse("Should be unblocked after toggle off", repository.isPackageBlockedSync("com.google.android.youtube"))
  }

  @Test
  fun `channel can be searched allowed and whitelisted for focus study`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.FocusSenseRepository(context)

    val channel = com.example.data.model.AllowedYouTubeChannelEntity(
      channelId = "test-striver-channel",
      childId = "child-default",
      channelName = "Striver",
      handle = "@takeUforward",
      description = "DSA and computer science tutorials",
      category = "Computer Science",
      channelUrl = "https://m.youtube.com/@takeUforward",
      isAllowed = true
    )
    repository.addYouTubeChannel(channel)

    repository.toggleYouTubeChannelAllowed("test-striver-channel", true)
    assertTrue("Channel should be allowed", channel.isAllowed)
    assertEquals("Striver", channel.channelName)
    assertEquals("@takeUforward", channel.handle)
  }

  @Test
  fun `parent role never records location or scans local apps as child telemetry`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.FocusSenseDatabase.getInstance(context)
    val parent = com.example.data.model.UserEntity(
      userId = "parent-test-1",
      groupId = "group-test-1",
      email = "parent@test.com",
      password = "secret",
      role = "parent",
      name = "Parent User"
    )
    val child = com.example.data.model.UserEntity(
      userId = "child-test-1",
      groupId = "group-test-1",
      email = "child@test.com",
      password = "secret",
      role = "child",
      name = "Child User"
    )
    db.userDao().insert(parent)
    db.userDao().insert(child)

    val repository = com.example.data.repository.FocusSenseRepository(context)
    repository.switchUser(parent)
    repository.setSelectedChild("child-test-1")

    // Parent must NOT record location under childId
    repository.recordLocation("child-test-1", 37.77, -122.41, 5f, "Parent GPS")
    val locAfterParent = db.locationDao().getAllLocationsSync().filter { it.childId == "child-test-1" }
    assertTrue("Parent must not record location as child telemetry", locAfterParent.isEmpty())

    // Parent must NOT record screen content under childId
    val ignoredLog = repository.processExtractedContent(
      childId = "child-test-1",
      packageName = "com.example.chat",
      appName = "Chat",
      contentTitle = "Window",
      extractedText = "Hello world",
      forceOffline = true
    )
    assertEquals("", ignoredLog.childId)
    assertEquals("Ignored on parent device", ignoredLog.aiAnalysisSummary)

    // Child CAN record location under its own authenticated userId
    repository.switchUser(child)
    repository.toggleNetworkSimulation(false)
    repository.recordLocation("ignored-arg", 40.7128, -74.0060, 6f, "Child Real Fix")
    val locAfterChild = db.locationDao().getAllLocationsSync().filter { it.childId == "child-test-1" }
    assertEquals(1, locAfterChild.size)
    assertEquals("Child Real Fix", locAfterChild.first().locationName)
  }

  @Test
  fun `model2 labels json has corrected not_cyberbullying label`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val labelsJson = context.assets.open("model2/labels.json").bufferedReader().use { it.readText() }
    val array = org.json.JSONArray(labelsJson)
    val labels = (0 until array.length()).map { array.getString(it) }
    assertTrue("labels.json must contain not_cyberbullying", labels.contains("not_cyberbullying"))
    assertFalse("labels.json must not contain typo", labels.contains("not_cybepprbullying"))
  }

  @Test
  fun `threat engine does not false-flag benign casual conversation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = ThreatEvaluationEngine(context)
    val benignSamples = listOf(
      "Hey can we review the biology homework at 4pm?",
      "Good morning! Did you finish the math worksheet yet?",
      "Let's play Minecraft together after dinner"
    )
    for (sample in benignSamples) {
      val result = engine.evaluateLayer2MobileBertML(
        appName = "WhatsApp",
        contentTitle = "Class Group",
        extractedText = sample
      )
      assertFalse("Benign text '$sample' should not be flagged as ${result.threatCategory}", result.isFlagged)
    }
  }

  @Test
  fun `multi-app pomodoro blocks selected apps only while running and unblocks when paused or stopped`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.repository.FocusSenseRepository(context)
    val targets = setOf("com.instagram.android", "com.zhiliaoapp.musically")

    repository.startPomodoro(targets, "Social Apps", 25)
    assertTrue("Instagram should be blocked while timer runs", repository.checkAppRestriction("child-default", "com.instagram.android").isBlocked)
    assertTrue("TikTok should be blocked while timer runs", repository.checkAppRestriction("child-default", "com.zhiliaoapp.musically").isBlocked)
    assertFalse("Unselected calculator app should remain allowed", repository.checkAppRestriction("child-default", "com.google.android.calculator").isBlocked)

    repository.pausePomodoro()
    assertFalse("Instagram must unblock immediately when timer is paused", repository.checkAppRestriction("child-default", "com.instagram.android").isBlocked)
    assertFalse("TikTok must unblock immediately when timer is paused", repository.checkAppRestriction("child-default", "com.zhiliaoapp.musically").isBlocked)

    repository.startPomodoro(targets, "Social Apps", 25)
    repository.stopPomodoro()
    assertFalse("Instagram must remain unblocked after timer stops", repository.checkAppRestriction("child-default", "com.instagram.android").isBlocked)
    assertFalse("TikTok must remain unblocked after timer stops", repository.checkAppRestriction("child-default", "com.zhiliaoapp.musically").isBlocked)
  }

  @Test
  fun `location label helper resolves place name and never outputs home`() {
    val sanitized = com.example.util.LocationGeocoderHelper.sanitizePlaceLabel("Home Street, Downtown", 37.7749, -122.4194)
    assertFalse("Location label must never expose 'home'", Regex("\\bhome\\b", RegexOption.IGNORE_CASE).containsMatchIn(sanitized))
    assertTrue("Location label should preserve coordinates", sanitized.contains("37.7749, -122.4194"))

    val blankFallback = com.example.util.LocationGeocoderHelper.sanitizePlaceLabel("", 40.7128, -74.0060)
    assertEquals("Area (40.7128, -74.0060)", blankFallback)
  }

  @Test
  fun `scraper detects private and incognito browser tabs and ignores normal browsing`() {
    val incognitoNodes = listOf(
      com.example.service.NodeTextData(
        text = "You've gone incognito",
        viewId = "com.android.chrome:id/new_tab_incognito_container"
      )
    )
    val chromeSignal = com.example.service.ScraperFilterEngine.detectPrivateBrowsingSignal(
      packageName = "com.android.chrome",
      nodes = incognitoNodes,
      windowTitle = null
    )
    assertTrue("Chrome incognito should be detected", !chromeSignal.isNullOrBlank())

    val edgeSignal = com.example.service.ScraperFilterEngine.detectPrivateBrowsingSignal(
      packageName = "com.microsoft.emmx",
      nodes = listOf(com.example.service.NodeTextData("InPrivate tab", isContentDesc = true)),
      windowTitle = null
    )
    assertTrue("Edge InPrivate should be detected", !edgeSignal.isNullOrBlank())

    val normalNodes = listOf(
      com.example.service.NodeTextData(
        text = "Khan Academy Photosynthesis",
        viewId = "com.android.chrome:id/url_bar"
      )
    )
    val normalSignal = com.example.service.ScraperFilterEngine.detectPrivateBrowsingSignal(
      packageName = "com.android.chrome",
      nodes = normalNodes,
      windowTitle = "Chrome"
    )
    assertEquals(null, normalSignal)
  }

  @Test
  fun `supabase app usage payload populates both child_id and subject_user_id UUIDs`() {
    val entries = listOf(
      com.example.data.repository.AppUsageEntry(
        packageName = "com.google.android.youtube",
        appName = "YouTube",
        category = "Video & Streaming",
        foregroundTimeMs = 1_200_000L,
        totalTimeMinutes = 20,
        launchCount = 4,
        isBlocked = false
      )
    )
    val (withId, noId) = com.example.data.remote.SupabaseClient.buildAppUsageStatsPayloads(
      childId = "child-default",
      dateKey = "2026-11-20",
      entries = entries,
      nowIso = "2026-11-20T12:00:00Z"
    )
    assertEquals(1, withId.length())
    assertEquals(1, noId.length())

    val rowWithId = withId.getJSONObject(0)
    val rowNoId = noId.getJSONObject(0)
    val expectedChildUuid = com.example.data.remote.SupabaseClient.toUuid("child-default")

    assertEquals(expectedChildUuid, rowWithId.getString("child_id"))
    assertEquals(expectedChildUuid, rowWithId.getString("subject_user_id"))
    assertEquals(expectedChildUuid, rowNoId.getString("subject_user_id"))
    assertTrue("Deterministic id should be non-blank", rowWithId.getString("id").isNotBlank())
    assertEquals("com.google.android.youtube", rowWithId.getString("package_name"))
    assertEquals(20, rowWithId.getInt("total_time_minutes"))
  }

  @Test
  fun `threat engine suppresses model2 false-flagging on benign YouTube text without bullying cues`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = ThreatEvaluationEngine(context)

    val pred2Bias = com.example.ai.Model2Prediction(
      label = "religion",
      confidence = 0.8556f,
      isCyberbullying = true
    )

    val safeResult = engine.combineModelPredictions(
      pred1 = com.example.ai.Prediction(label = "SAFE_GENERAL", confidence = 0.88f, escalate = false),
      pred2 = pred2Bias,
      appName = "YouTube",
      contentTitle = "Physics Lecture",
      extractedText = "Newton laws of motion full chapter explanation for class 9"
    )
    assertFalse("Benign text with SAFE_GENERAL M1 must not be flagged by noisy M2 religion score", safeResult.isFlagged)
    assertEquals("Safe", safeResult.threatCategory)

    val unknownResult = engine.combineModelPredictions(
      pred1 = com.example.ai.Prediction(label = "UNKNOWN", confidence = 0.30f, escalate = true),
      pred2 = pred2Bias,
      appName = "YouTube",
      contentTitle = "Coding Tutorial",
      extractedText = "Kotlin Jetpack Compose beginner tutorial build your first app"
    )
    assertFalse("Benign text with UNKNOWN M1 and no bullying cue must not be flagged", unknownResult.isFlagged)
    assertEquals("Safe", unknownResult.threatCategory)

    val genuineBullying = engine.combineModelPredictions(
      pred1 = com.example.ai.Prediction(label = "UNKNOWN", confidence = 0.45f, escalate = true),
      pred2 = pred2Bias,
      appName = "Discord",
      contentTitle = "Chat",
      extractedText = "you are a worthless loser nobody likes you go die"
    )
    assertTrue("Explicit bullying cue with high M2 confidence should be flagged", genuineBullying.isFlagged)
  }

  @Test
  fun `scraper filters noisy YouTube chrome and detects explicit bullying cues`() {
    val nodes = listOf(
      com.example.service.NodeTextData("Shorts"),
      com.example.service.NodeTextData("Subscribe"),
      com.example.service.NodeTextData("Share"),
      com.example.service.NodeTextData("1.2M views"),
      com.example.service.NodeTextData("3 days ago"),
      com.example.service.NodeTextData("03:45 / 12:30"),
      com.example.service.NodeTextData("Khan Academy Organic Chemistry Basics")
    )
    val filtered = com.example.service.ScraperFilterEngine.filterAndClassifyWindow(
      packageName = "com.google.android.youtube",
      ownPackage = "com.focussense.family",
      className = "com.google.android.youtube.HomeActivity",
      nodes = nodes
    )
    assertFalse("Benign educational YouTube video should not be ignored or flagged", filtered.isIgnored)
    assertEquals("Khan Academy Organic Chemistry Basics", filtered.cleanText)
    assertEquals(6, filtered.noiseNodesFiltered)
    assertFalse("Should not escalate benign content", filtered.needsEscalation)

    assertTrue(
      "Explicit bullying cue must trigger",
      com.example.service.ScraperFilterEngine.hasExplicitBullyingOrHarmSignal("nobody likes you loser disappear")
    )
    assertFalse(
      "Normal study video text should not trigger bullying cue",
      com.example.service.ScraperFilterEngine.hasExplicitBullyingOrHarmSignal("organic chemistry lecture chapter 4")
    )
  }
}
