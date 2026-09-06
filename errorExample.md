

I was using the converter and got the cfollowing:


```
md2latex: error:
{
  "details" : [
    " better-thought.manifest: 'dialect' is no longer used. Source is always read as Markua, which also accepts Docusaurus ::: admonitions, so there is nothing left to choose. Delete the line."
  ],
  "errorDescription" : " better-thought.manifest: 'dialect' is no longer used. Source is always read as Markua, which also accepts Docusaurus ::: admonitions, so there is nothing left to choose. Delete the line.",
  "stackTrace" : [
    "better-thought.manifest: 'dialect' is no longer used. Source is always read as Markua, which also accepts Docusaurus ::: admonitions, so there is nothing left to choose. Delete the line.",
    "  com.purplehillsbooks.md2latex.ManifestReader$YamlMap.rejectRemovedKeys(ManifestReader.java:465)",
    "  com.purplehillsbooks.md2latex.ManifestReader.read(ManifestReader.java:155)",
    "  com.purplehillsbooks.md2latex.Main.build(Main.java:68)",
    "  com.purplehillsbooks.md2latex.Main.run(Main.java:57)",
    "  com.purplehillsbooks.md2latex.Main.main(Main.java:23)"
  ]
}
```

ok, that is not such a bad error, but I am mindful that there are a lot of ways that the manifest file might fail to be loaded.  This is the case where it would make sense to add a wrapping exception around the manifest reader to state that whatever error occurred within that would be clearly indicates as being part of the reading of the manifest.

So I added in:

```java
try {
    // reading code here
} catch (Exception e) {
    throw CommonException.newWrap("cannot read manifest file '%s'", e, manifestFile);
}
```

I ran again, and 20 tests failed:

```
[ERROR] Failures:
[ERROR]   ManifestReaderTest.aMissingFileInAnySectionIsReported:339->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.badCodeStyleIsRejected:459->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.badDocumentClassIsRejected:446->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.dialectIsRejectedWithAnExplanation:81->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.docusaurusNeedsADirectory:143->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.duplicateKeysAreRejected:534->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.emptyChaptersIsRejected:391->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.emptyManifestIsRejected:528->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.entryWithBothFileAndPartIsRejected:483->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.entryWithNeitherFileNorPartIsRejected:471->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.everyMissingSourceFileIsReportedAtOnce:429->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.latexAndOutputCannotBothBeGiven:172->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.malformedYamlIsReportedAsSuch:523->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.missingChaptersIsRejected:385->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.missingTitleIsRejected:376->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.scalarAtTopLevelIsRejected:545->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.theRemovedSourceDirKeyGetsAPointedExplanation:496->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.unknownDocusaurusFormatIsRejected:157->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.unknownNestedKeyIsRejected:415->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
[ERROR]   ManifestReaderTest.unknownTopLevelKeyIsRejected:402->errorFrom:42 Unexpected exception type thrown, expected: <com.purplehillsbooks.md2latex.ManifestException> but was: <com.purplehillsbooks.exception.CommonException>
```

The tests were fundamentally checking to see if a particular operation failed in a particular input, and they did.  Towever the tests were checking that a specific exception was being thrown.  This harkens back to two specific outdated idea:

1. that all you need to know is the class of the exception.  That for 1000 possible error conditions, you will have a unique exception class for each.  This does not scale because in any system of any complexity, there are literally thousands of different things that the inputs might have wrong, and you want to check for all, and warn the user when they do it.
2. that the error message itself is unimportant and can be ignored.

The opposite should be the case: we should be checking that an exception was caught, and that the exception message said something about the problem.  For example, if the input should have had a closing brace, the error should mention something about closing brace.  What the class of the exception is does not matter at all.

Furthermore the tests were fragile.  I *improved* the error messages, but the improvement broke 20 tests, because the tests were not actually checking the message that was output, but only the exception class (as if that is all that mattered)

The correct solution is to have the test check that the message of the exception included something about the actual problem, and it should ignore extra details which the test does not need to check.  The solution is to get the text of the entire chain of messages, and then look to see if a particular phrase is within them.

In the `CommonException` class, this is done with the `getFullMessage()` method.

The test used to have:

```java
return assertThrows(ManifestException.class, () -> ManifestReader.readManifest(m))
        .getMessage();
```

but was changed to:

```
try {
    ManifestReader.readManifest(m);
    throw CommonException.newBasic("did not receive an exception for " + m + ":\n" + yaml);
} catch (Exception e) {
    return CommonException.getFullMessage(e);
}
```

If it fails to throw an exception, the test will have a clear report of that.  Otherwise, the entire exception chain is tapped for checking for patterns.  That one change fixed all 20 test cases.

