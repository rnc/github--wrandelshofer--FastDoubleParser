package ch.randelshofer.fastdoubleparser;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class JavaBigIntegerFromByteArrayTest extends AbstractBigIntegerParserTest {


    private void test(BigIntegerTestData d, Function<BigIntegerTestData, BigInteger> f) {
        BigInteger expectedValue = d.expectedValue().get();
        BigInteger actual = f.apply(d);
        if (expectedValue != null) {
            assertEquals(0, expectedValue.compareTo(actual),
                    "expected:" + expectedValue + " <> actual:" + actual);
            assertEquals(expectedValue, actual);
        } else {
            assertNull(actual);
        }
    }

    @TestFactory
    public Stream<DynamicTest> dynamicTestsParseBigDecimalFromByteArray() {
        return createRegularTestData().stream()
                .filter(t -> t.charLength() == t.input().length()
                        && t.charOffset() == 0)
                .map(t -> dynamicTest(t.title(),
                        () -> test(t, u -> JavaBigIntegerParser.parseBigIntegerOrNull(u.input().getBytes(StandardCharsets.ISO_8859_1),
                                u.byteOffset(), u.byteLength()))));

    }
}
