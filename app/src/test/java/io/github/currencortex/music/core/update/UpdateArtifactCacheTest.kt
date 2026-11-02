package io.github.currencortex.music.core.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateArtifactCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun root() = temporary.newFolder("updates")
    private fun name(id: Int) = "update-${id.toString(16).padStart(64, '0')}.apk"
    private fun artifact(directory: File, id: Int, size: Int = 4, age: Long = id.toLong()): File {
        directory.mkdirs()
        return File(directory, name(id)).apply {
            writeBytes(ByteArray(size) { id.toByte() })
            Files.setLastModifiedTime(toPath(), FileTime.fromMillis(age * 1000))
        }
    }

    @Test fun countQuotaSpansForegroundAndWorkerFilesAndOnlyRemovesEmptyUuidDirectories() {
        val root = root()
        val oldWorker = File(root, UUID.randomUUID().toString())
        val old = artifact(oldWorker, 1)
        val second = artifact(root, 2)
        val third = artifact(root, 3)
        val latestWorker = File(root, UUID.randomUUID().toString())
        val latest = artifact(latestWorker, 4)
        val result = UpdateArtifactCache(root).prune()
        assertFalse(old.exists()); assertFalse(second.exists())
        assertTrue(third.isFile); assertTrue(latest.isFile)
        assertFalse("Only a confirmed empty UUID directory may be removed", oldWorker.exists())
        assertTrue(latestWorker.isDirectory); assertTrue(root.isDirectory)
        assertEquals(2, result.removedFiles); assertEquals(2, result.completedFiles)
        assertEquals(8L, result.completedBytes)
    }

    @Test fun byteQuotaAndExactBoundaryAreIndependentOfCountQuota() {
        val root = root()
        val oldest = artifact(root, 1, 6)
        val middle = artifact(root, 2, 6)
        val newest = artifact(root, 3, 6)
        val result = UpdateArtifactCache(root, maxCompletedFiles = 10, maxCompletedBytes = 10).prune()
        assertFalse(oldest.exists()); assertFalse(middle.exists()); assertTrue(newest.exists())
        assertEquals(6L, result.completedBytes)
        newest.delete()
        artifact(root, 4, 5); artifact(root, 5, 5)
        val exact = UpdateArtifactCache(root, maxCompletedBytes = 10).prune()
        assertEquals(0, exact.removedFiles); assertEquals(2, exact.completedFiles); assertEquals(10L, exact.completedBytes)
    }

    @Test fun leasesProtectActiveApkPartAndEmptyDirectoryAcrossCacheInstances() {
        val root = root()
        val worker = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        val target = File(worker, name(1))
        val writer = UpdateArtifactCache(root, maxCompletedFiles = 1, maxCompletedBytes = 1)
        val lease = writer.lease(target)
        try {
            assertTrue(worker.isDirectory)
            target.writeBytes(ByteArray(16))
            val part = File(worker, "${target.name}.part").apply { writeBytes(ByteArray(12)) }
            val stale = File(root, "${name(2)}.part").apply { writeText("abandoned") }
            val result = UpdateArtifactCache(root, maxCompletedFiles = 1, maxCompletedBytes = 1).prune()
            assertTrue(target.isFile); assertTrue(part.isFile); assertFalse(stale.exists())
            assertEquals("Active artifacts can temporarily exceed quota", 16L, result.completedBytes)
        } finally { lease.close() }
        assertFalse(target.exists()); assertFalse(File(worker, "${target.name}.part").exists())
        assertFalse(worker.exists())
    }

    @Test fun returnedTargetsRemainProtectedAndThirdCompletionReclaimsOldest() {
        val root = root()
        var clock = 10_000L
        val files = (1..3).map { File(root, name(it)) }
        for (file in files) {
            val cache = UpdateArtifactCache(root, nowMillis = { clock++ })
            cache.lease(file).use { lease ->
                file.writeBytes(ByteArray(4)); lease.ensureSafe(); lease.completed()
            }
            assertTrue("A download's just-returned target must still exist", file.isFile)
        }
        assertFalse(files[0].exists()); assertTrue(files[1].exists()); assertTrue(files[2].exists())
        val result = UpdateArtifactCache(root, maxCompletedBytes = 1).prune()
        assertEquals("Do not delete freshly returned installation targets to satisfy a tighter quota", 2, result.completedFiles)
    }

    @Test fun duplicateWriterCannotDeleteTheFirstWritersPartialFile() {
        val root = root()
        val file = File(root, name(1))
        UpdateArtifactCache(root).lease(file).use {
            val part = File(root, "${file.name}.part").apply { writeText("active bytes") }
            assertThrows(IOException::class.java) { UpdateArtifactCache(root).lease(file) }
            assertEquals("active bytes", part.readText())
        }
    }

    @Test fun foreignFilesDeepPathsAndSiblingDownloadsAreNeverTraversedOrRemoved() {
        val root = root()
        val downloads = temporary.newFolder("Downloads")
        val userFile = artifact(downloads, 1)
        val foreignTree = File(root, "foreign").apply { mkdir() }
        val foreignApk = artifact(foreignTree, 2)
        val worker = File(root, UUID.randomUUID().toString()).apply { mkdir() }
        val deepApk = artifact(File(worker, "nested"), 3)
        val note = File(worker, "readme.txt").apply { writeText("keep") }
        val foreignNames = listOf("user.apk", "update-${"A".repeat(64)}.apk", "${name(9)}.bak", "update-short.apk.part")
            .map { File(root, it).apply { writeText("keep") } }
        (4..6).forEach { artifact(root, it) }
        UpdateArtifactCache(root).prune()
        assertTrue(userFile.isFile); assertTrue(foreignApk.isFile); assertTrue(deepApk.isFile)
        assertTrue(note.isFile); foreignNames.forEach { assertTrue(it.isFile) }
        assertTrue(worker.isDirectory); assertTrue(foreignTree.isDirectory)
        assertThrows(IllegalArgumentException::class.java) { UpdateArtifactCache(root).lease(userFile) }
        assertThrows(IllegalArgumentException::class.java) { UpdateArtifactCache(root).lease(File(root, "foreign/../${name(10)}")) }
    }

    @Test fun symlinkFilesDirectoriesAndRootAreNotFollowed() {
        val root = root()
        val outside = temporary.newFolder("outside")
        val outsideApk = artifact(outside, 1)
        val fileLink = File(root, name(2)).toPath()
        val directoryLink = File(root, UUID.randomUUID().toString()).toPath()
        val rootLink = File(temporary.root, "linked-updates").toPath()
        try {
            val supported = runCatching {
                Files.createSymbolicLink(fileLink, outsideApk.toPath())
                Files.createSymbolicLink(directoryLink, outside.toPath())
                Files.createSymbolicLink(rootLink, root.toPath())
            }.isSuccess
            assumeTrue("Host does not permit symlink creation; boundary scenario remains unverified", supported)
            (3..5).forEach { artifact(root, it) }
            UpdateArtifactCache(root).prune()
            assertTrue(outsideApk.isFile); assertTrue(Files.isSymbolicLink(fileLink)); assertTrue(Files.isSymbolicLink(directoryLink))
            assertThrows(IllegalStateException::class.java) { UpdateArtifactCache(root).lease(fileLink.toFile()) }
            assertThrows(IllegalStateException::class.java) { UpdateArtifactCache(root).lease(directoryLink.resolve(name(8)).toFile()) }
            assertEquals(0, UpdateArtifactCache(rootLink.toFile()).prune().removedFiles)
            assertThrows(IllegalStateException::class.java) { UpdateArtifactCache(rootLink.toFile()).lease(rootLink.resolve(name(9)).toFile()) }
        } finally {
            for (path in listOf(fileLink, directoryLink, rootLink)) if (Files.isSymbolicLink(path)) Files.delete(path)
        }
    }
}
