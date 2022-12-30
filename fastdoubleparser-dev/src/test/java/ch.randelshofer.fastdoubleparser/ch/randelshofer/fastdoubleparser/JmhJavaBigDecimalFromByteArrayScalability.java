/*
 * @(#)JmhJavaBigDecimalFromByteArrayScalability.java
 * Copyright © 2022 Werner Randelshofer, Switzerland. MIT License.
 */
package ch.randelshofer.fastdoubleparser;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static ch.randelshofer.fastdoubleparser.Strings.repeat;

/**
 * Benchmarks for selected floating point strings.
 * <pre>
 * # JMH version: 1.35
 * # VM version: JDK 20-ea, OpenJDK 64-Bit Server VM, 20-ea+29-2280
 * # Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz
 *      (digits)  Mode  Cnt            Score   Error  Units
 * par        24  avgt                77.542          ns/op
 * par         1  avgt                10.387          ns/op
 * par        10  avgt                16.126          ns/op
 * par       100  avgt               218.620          ns/op
 * par      1000  avgt              4804.543          ns/op
 * par     10000  avgt            117301.625          ns/op
 * par    100000  avgt           3805758.892          ns/op
 * par   1000000  avgt          55026280.527          ns/op
 * par  10000000  avgt       1_455180955.000          ns/op
 * par 100000000  avgt      33_793710446.000          ns/op
 * par 646391315  avgt     393_551821905.000          ns/op
 * seq        24  avgt                68.342          ns/op
 * seq         1  avgt                 7.347          ns/op
 * seq        10  avgt                14.433          ns/op
 * seq       100  avgt               217.995          ns/op
 * seq      1000  avgt              4784.030          ns/op
 * seq     10000  avgt            166472.648          ns/op
 * seq    100000  avgt           6472800.381          ns/op
 * seq   1000000  avgt         111609901.267          ns/op
 * seq  10000000  avgt       2_418388865.000          ns/op
 * seq 100000000  avgt      41_679193822.000          ns/op
 * seq 646391315  avgt     400_608301899.000          ns/op
 *
 *       (digits)  Mode  Cnt     _        Score   Error  Units
 * seq         24  avgt          _       78.961          ns/op
 * seq          1  avgt          _       10.397          ns/op
 * seq         10  avgt          _       17.432          ns/op
 * seq        100  avgt          _      232.310          ns/op
 * seq       1000  avgt          _     5085.790          ns/op
 * seq      10000  avgt          _   178608.728          ns/op
 * seq     100000  avgt          _  6997056.928          ns/op
 * seq    1000000  avgt          _224134358.400          ns/op
 * seq   10000000  avgt         4_307235737.000          ns/op
 * seq  100000000  avgt       105_586368850.000          ns/op
 * seq  646391315  avgt       627_362411969.000          ns/op
 *
 * recursive only (recursion threshold=0)
 *      (digits)  Mode  Cnt      _        Score   Error  Units
 * rec        24  avgt           _      122.948          ns/op
 * rec         1  avgt           _        8.775          ns/op
 * rec        10  avgt           _       17.646          ns/op
 * rec       100  avgt           _      703.225          ns/op
 * rec      1000  avgt           _     8707.115          ns/op
 * rec     10000  avgt           _   229812.381          ns/op
 * rec    100000  avgt           _  7127677.071          ns/op
 * rec   1000000  avgt           _229351230.159          ns/op
 * rec  10000000  avgt          4_322373197.000          ns/op
 *
 * iterative only (recursion threshold=Integer.MAX_VALUE)
 *      (digits)  Mode  Cnt     _        Score   Error  Units
 * itr        24  avgt          _       85.368          ns/op
 * itr         1  avgt          _       11.053          ns/op
 * itr        10  avgt          _       18.350          ns/op
 * itr       100  avgt          _      233.284          ns/op
 * itr      1000  avgt          _     6581.352          ns/op
 * itr     10000  avgt          _   539732.484          ns/op
 * itr    100000  avgt          _ 51324255.979          ns/op
 * itr   1000000  avgt         5_049179432.000          ns/op
 * itr  10000000  avgt       537_316705670.000          ns/op
 * </pre>
 *
 * <pre>
 * # JMH version: 1.35
 * # VM version: OpenJDK 64-Bit Server VM, Oracle Corporation, 20-ea+22-1594
 * # Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz
 * parse only:
 *         (digits)  Mode  Cnt          Score   Error  Units
 * fprs           1  avgt    2         10.046          ns/op
 * fprs          10  avgt    2         14.924          ns/op
 * fprs         100  avgt    2         21.201          ns/op
 * fprs        1000  avgt    2         75.387          ns/op
 * fprs       10000  avgt    2        629.977          ns/op
 * fprs      100000  avgt    2       7197.921          ns/op
 * fprs     1000000  avgt    2      93134.505          ns/op
 * fprs    10000000  avgt    2    1324385.898          ns/op
 * fprs   100000000  avgt    2   13841659.306          ns/op
 * fprs  1000000000  avgt    2  139549257.979          ns/op
 * fprs  1292782621  avgt    2  183389538.331          ns/op
 * iprs           1  avgt    2          7.095          ns/op
 * iprs          10  avgt    2         12.980          ns/op
 * iprs         100  avgt    2         18.822          ns/op
 * iprs        1000  avgt    2         81.936          ns/op
 * iprs       10000  avgt    2        712.674          ns/op
 * iprs      100000  avgt    2       6844.904          ns/op
 * iprs     1000000  avgt    2      86105.059          ns/op
 * iprs    10000000  avgt    2    1241240.895          ns/op
 * iprs   100000000  avgt    2   12981130.860          ns/op
 * iprs  1000000000  avgt    2  133794721.567          ns/op
 * iprs  1292782621  avgt    2  170464284.271          ns/op
 * </pre>
 */

@Fork(value = 1, jvmArgsAppend = {
        "-XX:+UnlockExperimentalVMOptions", "--add-modules", "jdk.incubator.vector"
        , "--enable-preview"
        , "-Xmx24g"

        //       ,"-XX:+UnlockDiagnosticVMOptions", "-XX:PrintAssemblyOptions=intel", "-XX:CompileCommand=print,ch/randelshofer/fastdoubleparser/JavaBigDecimalParser.*"

})
@Measurement(iterations = 1)
@Warmup(iterations = 0)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class JmhJavaBigDecimalFromByteArrayScalability {
    @Param({
            // "24"
            // , "1"
            // , "10"
            // , "100"
            // , "1000"
            // , "10000"
            // , "100000"
            // , "1000000"
            // , "10000000"
            //  "100000000"
            "646391315"// The maximal number non-zero digits in the significand

    })
    public int digits;
    private byte[] str;

    @Setup(Level.Trial)
    public void setUp() {
        str = repeat("9806543217", (digits + 9) / 10).substring(0, digits).getBytes(StandardCharsets.ISO_8859_1);
    }

    @Benchmark
    public BigDecimal seq() {
        return JavaBigDecimalParser.parseBigDecimal(str);
    }

    @Benchmark
    public BigDecimal par() {
        return JavaBigDecimalParser.parallelParseBigDecimal(str);
    }
}











