package rs.project1k.plan;

import java.util.Arrays;
import java.util.Collections;

/** Pure Java only; no Android SDK, Gradle or emulator needed. */
public final class UniqueValuesTest {
    private static int cases;
    private static void allowed(Object... values) {
        UniqueValues.requireDistinct(Arrays.asList(values), "Naziv");
        cases++;
    }
    private static void duplicate(Object... values) {
        try {
            UniqueValues.requireDistinct(Arrays.asList(values), "Naziv");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().contains("Naziv")) throw new AssertionError("Missing field name");
            cases++;
            return;
        }
        throw new AssertionError("Expected duplicate rejection");
    }
    public static void main(String[] args) {
        duplicate("Ana", "Ana");
        duplicate(" Ana ", "Ana");
        allowed("Ana", "ana");
        allowed(null, null, "", " ", Collections.emptyList(), Collections.emptyList());
        duplicate(0L, 0L);
        duplicate(false, false);
        allowed(false, true);
        duplicate(1, 1.0);
        duplicate(-0.0, 0L);
        allowed(9007199254740992L, 9007199254740993L);
        duplicate(Arrays.asList("a", "b"), Arrays.asList("b", "a"));
        duplicate(Arrays.asList("a", "a"), Collections.singletonList("a"));
        allowed(Arrays.asList("a,b", "c"), Arrays.asList("a", "b,c"));
        allowed(Collections.singletonList("a"), "a");
        // Checking the candidate is independent of how it was entered (widget or full form).
        duplicate("Existing", "New", "Existing");
        allowed("Existing", "New");
        UniqueValues.requireDistinctTuples(Arrays.asList(Arrays.asList("Test 1", "Ana"), Arrays.asList("Test 1", "Maja"), Arrays.asList("Test 2", "Ana")), "Test + Meta");
        cases++;
        UniqueValues.requireDistinctTuples(Arrays.asList(Arrays.asList("Test 1", null), Arrays.asList("Test 1", null)), "Test + Meta");
        cases++;
        boolean rejected=false;
        try {
            UniqueValues.requireDistinctTuples(Arrays.asList(Arrays.asList("Test 1", "Ana"), Arrays.asList("Test 1", "Ana")), "Test + Meta");
        } catch(IllegalArgumentException expected) { rejected=true; }
        if(!rejected)throw new AssertionError("Duplicate combination accepted");
        cases++;
        UniqueValues.requireDistinctTuples(Arrays.asList(Arrays.asList("a:b", "c"), Arrays.asList("a", "b:c")), "A + B");
        cases++;
        System.out.println(cases + " uniqueness cases passed");
    }
}
