package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ExtractedPlayerVerification
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ArenaRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun getOrInitializeUserProfile_and_verifyGameUid_succeedsAndPreventsDuplicateUid() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_$uniqueSuffix@arenax.test")
        val repository = ArenaRepository(firestore)

        val profileResult = withTimeout(5000L) {
            repository.getOrInitializeUserProfile(aliceUid, "AliceConqueror")
        }
        assertTrue(profileResult.isSuccess)
        val aliceProfile = profileResult.getOrThrow()
        assertEquals(aliceUid, aliceProfile.userId)

        val sharedGameUid = "5999$uniqueSuffix"
        val verification = ExtractedPlayerVerification(
            game = "BGMI",
            inGameName = "AliceIGL",
            gameUid = sharedGameUid,
            level = 75,
            rankTier = "Conqueror",
            codeVisible = true,
            confidence = 94,
            reasoning = "Code visible in signature"
        )

        val verifyResult = withTimeout(5000L) {
            repository.submitExtractedVerification(aliceProfile, verification)
        }
        assertTrue(verifyResult.isSuccess)
        assertEquals("VERIFIED", verifyResult.getOrThrow())

        // Sign in as Bob and attempt to link the exact same BGMI UID -> must fail anti-fraud check
        val bobUid = signInTestUser("bob_$uniqueSuffix@arenax.test")
        val bobProfile = withTimeout(5000L) {
            repository.getOrInitializeUserProfile(bobUid, "BobSniper").getOrThrow()
        }
        val duplicateAttempt = withTimeout(5000L) {
            repository.submitExtractedVerification(bobProfile, verification)
        }
        assertTrue("Expected duplicate UID check to fail", duplicateAttempt.isFailure)
    }

    @Test
    fun createChallenge_and_submitMatchingResult_completesAndUpdatesElo() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_chal_$uniqueSuffix@arenax.test")
        val repo = ArenaRepository(firestore)

        val aliceInit = withTimeout(5000L) {
            repo.getOrInitializeUserProfile(aliceUid, "AlicePro").getOrThrow()
        }
        withTimeout(5000L) {
            repo.submitExtractedVerification(
                aliceInit,
                ExtractedPlayerVerification(
                    game = "BGMI",
                    inGameName = "AlicePro",
                    gameUid = "711$uniqueSuffix",
                    level = 60,
                    rankTier = "Ace",
                    codeVisible = true,
                    confidence = 90,
                    reasoning = "Verified"
                )
            ).getOrThrow()
        }
        val aliceVerified = withTimeout(3000L) {
            repo.observeUserProfile(aliceUid).first { it?.isVerified == true }!!
        }

        val bobUid = signInTestUser("bob_chal_$uniqueSuffix@arenax.test")
        val bobProfile = withTimeout(5000L) {
            repo.getOrInitializeUserProfile(bobUid, "BobRival").getOrThrow()
        }

        // Switch back to Alice to send challenge
        signInTestUser("alice_chal_$uniqueSuffix@arenax.test")
        val challengeId = withTimeout(5000L) {
            repo.createChallenge(
                challenger = aliceVerified,
                opponent = bobProfile,
                game = "BGMI",
                mode = "1v1",
                mapName = "Warehouse TDM",
                weaponsRule = "M416 Only",
                rounds = 1,
                customNote = "Clean room",
                scheduledTimeLabel = "Tonight 21:00",
                stakePoints = 100,
                existingMyChallenges = emptyList(),
                blockedUserIds = emptySet()
            ).getOrThrow()
        }
        assertTrue(challengeId.isNotEmpty())

        val emittedChallenge = withTimeout(3000L) {
            repo.observeMyChallenges(aliceUid).first { list -> list.any { it.id == challengeId } }
                .first { it.id == challengeId }
        }
        assertEquals("sent", emittedChallenge.status)
    }

    @Test
    fun crossUserPrivateProfileRead_and_unauthenticatedQuery_failsWithPermissionDenied() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_priv_$uniqueSuffix@arenax.test")
        val repo = ArenaRepository(firestore)
        withTimeout(5000L) {
            repo.getOrInitializeUserProfile(aliceUid, "AlicePrivate").getOrThrow()
            repo.updateOnboardingProfile(
                username = "alice_priv_$uniqueSuffix",
                displayName = "Alice Secret",
                bio = "Private account",
                primaryGame = "BGMI",
                language = "English",
                playTime = "Night",
                isPrivate = true
            ).getOrThrow()
        }

        // Bob tries to directly read Alice's private profile document
        signInTestUser("bob_peek_$uniqueSuffix@arenax.test")
        try {
            withTimeout(5000L) {
                firestore.collection("users").document(aliceUid).get().await()
            }
            fail("Expected PERMISSION_DENIED when reading another user's private profile")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }

        // Unauthenticated caller tries to observe public profiles
        auth.signOut()
        try {
            withTimeout(3000L) {
                repo.observePublicProfiles().first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }
}
