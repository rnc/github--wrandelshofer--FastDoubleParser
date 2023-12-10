[![Maven Central](https://maven-badges.herokuapp.com/maven-central/ch.randelshofer/fastdoubleparser/badge.svg)](https://maven-badges.herokuapp.com/maven-central/ch.randelshofer/fastdoubleparser)

# FastDoubleParser

This is a Java port of Daniel Lemire's [fast_float](https://github.com/fastfloat/fast_float) project.

This project provides parsers for `double`, `float`, `BigDecimal` and `BigInteger` values.
The `double` and `float` parsers are optimised for speed for the most common inputs.
The `BigDecimal` and `BigInteger` parsers are optimised for speed on all inputs.

The code in this project contains optimised versions for Java SE 1.8, 11, 17, 19 and 20.
The code is released in a single multi-release jar, which contains the code for all these versions
except 20.

## License

### Project License

This project can be licensed under the
[MIT License](https://github.com/wrandelshofer/FastDoubleParser/blob/645dcc236687d22897406ddfeac45fa52d292580/LICENSE).

### Code License

Some code *in* this project is derived from the following projects:

- [fast_float](https://github.com/fastfloat/fast_float), licensed
  under [MIT License](https://github.com/fastfloat/fast_float/blob/35d523195bf7d57aba0e735ad6eba1e6f71ba8d6/LICENSE-MIT)
- [bigint](https://github.com/tbuktu/bigint/tree/floatfft), licensed
  under [BSD 2-clause License](https://github.com/tbuktu/bigint/blob/617c8cd8a7c5e4fb4d919c6a4d11e2586107f029/LICENSE)
-

The code is marked as such.

If you redistribute code, you must follow the terms of all involved licenses (MIT License, BSD 2-clause License).

The build scripts in this project do include the license files into the jar files.
So that the released jar files automatically comply with the licenses, when you use them.

## Usage

```java
module MyModule {
    requires ch.randelshofer.fastdoubleparser;
}
```

```java
import ch.randelshofer.fastdoubleparser.JavaDoubleParser;
import ch.randelshofer.fastdoubleparser.JavaFloatParser;
import ch.randelshofer.fastdoubleparser.JavaBigDecimalParser;
import ch.randelshofer.fastdoubleparser.JavaBigIntegerParser;
import ch.randelshofer.fastdoubleparser.JsonDoubleParser;

class MyMain {
    public static void main(String... args) {
        double d = JavaDoubleParser.parseDouble("1.2345e135");
        float f = JavaFloatParser.parseFloat("1.2345f");
        BigDecimal bd = JavaBigDecimalParser.parseBigDecimal("1.2345");
        BigInteger bi = JavaBigIntegerParser.parseBigInteger("12345");
        double jsonD = JsonDoubleParser.parseDouble("1.2345e85");
    }
}
```

The `parse...()`-methods take a `CharacterSequence`. a `char`-array or a `byte`-array as argument. This way, you can
parse from a `StringBuffer` or an array without having to convert your input to a `String`. Parsing from an array is
faster, because the parser can process multiple characters at once using SIMD instructions.

## Performance Tuning

The JVM does not reliably inline `String.charAt(int)`. This may negativily impact the
`parse...()`-methods that take a `CharacterSequence` as an argument.

To ensure optimal performance, you can use the following java command line option:

    -XX:CompileCommand=inline,java/lang/String.charAt

## Performance Characteristics

### `float` and `double` parsers

On common input data, the fast `double` and `float` parsers are about 4 times faster than
`java.lang.Double.valueOf(String)` and `java.lang.Float.valueOf(String)`.

For less common inputs, the fast parsers can be slower than their `java.lang` counterparts.

A `double` value can always be specified exactly with up to 17 digits in the significand.
A `float` only needs up to 8 digits.
Therefore, inputs with more than 19 digits in the significand are considered less common.
Such inputs are expected to occur if the input data was created with more precision, and needs to be narrowed down
to the precision of a `double` or a `float`.

### `BigDecimal` and `BigInteger` parsers

On common input data, the fast `BigDecimal` and `BigInteger` parsers are slightly faster than
`java.math.BigDecimal(String)` and `java.math.BigInteger(String)`.

For less common inputs with many digits, the fast parsers can be a lot faster than their `java.math` counterparts.
The fast parsers can convert even the longest supported inputs in less than 6 minutes, whereas
their `java.math` counterparts need months!

The fast parsers convert digit characters from base 10 to a bit sequence in base 2
using a divide-and-conquer algorithm. Small sequences of digits are converted
individually to bit sequences and then gradually combined to the final bit sequence.
This algorithm needs to perform multiplications of very long bit sequences.
The multiplications are performed in the frequency domain using a discrete fourier transform.
The multiplications in the frequency domain can be performed in `O(N log N (log log N))` time,
where `N` is the number of digits.
In contrast, conventional multiplication algorithms in the time domain need `O(N²)` time.


### Memory usage and computation time

The memory usage depends on the result type and the maximal supported input character length.

The computation times are given for a Mac mini 2018 with Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz.

| Parser               |Result Type          | Maximal<br/>input length | Memory usage<br/>JVM -Xmx | Computation<br/>Time |
|----------------------|---------------------|---------------------:|--------------------------:|---------------------:|
| JavaDoubleParser     |java.lang.Double     |             2^31 - 5 |              10 gigabytes |              < 5 sec |
| JavaFloatParser      |java.lang.Float      |             2^31 - 5 |              10 gigabytes |              < 5 sec |
| JavaBigIntegerParser |java.math.BigInteger |        1,292,782,622 |              16 gigabytes |              < 6 min |
| JavaBigDecimalParser |java.math.BigDecimal |        1,292,782,635 |              16 gigabytes |              < 6 min |

## Performance measurements

### Random double numbers in the range from 0 to 1

Most input lines look like this: `0.4011441469603171`.

Mac Mini (2018),
CPU: Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz,
OS: Mac OS X, 13.4.1, 12 processors available

| Method                      |   MB/s |  stdev | Mfloats/s |   ns/f | speedup | JDK   |
|-----------------------------|-------:|-------:|----------:|-------:|--------:|-------|
| java.lang.Double            |  85.88 |  5.7 % |      4.93 | 202.90 |    1.00 | 21-ea |
| java.lang.Float             |  89.79 |  5.5 % |      5.15 | 194.05 |    1.00 | 21-ea |
| java.math.BigDecimal        | 170.06 |  5.9 % |      9.76 | 102.46 |    1.00 | 21-ea |
| JavaDoubleParser String     | 529.33 | 17.4 % |     30.38 |  32.92 |    6.16 | 21-ea |
| JavaDoubleParser char[]     | 684.10 |  4.2 % |     39.26 |  25.47 |    7.97 | 21-ea |
| JavaDoubleParser byte[]     | 677.93 | 10.2 % |     38.91 |  25.70 |    7.89 | 21-ea |
| JsonDoubleParser String     | 521.02 | 15.6 % |     29.90 |  33.44 |    6.07 | 21-ea |
| JsonDoubleParser char[]     | 601.50 | 14.4 % |     34.52 |  28.97 |    7.00 | 21-ea |
| JsonDoubleParser byte[]     | 632.71 | 12.2 % |     36.31 |  27.54 |    7.37 | 21-ea |
| JavaFloatParser  String     | 482.27 | 14.6 % |     27.68 |  36.13 |    5.37 | 21-ea |
| JavaFloatParser  char[]     | 674.04 | 14.6 % |     38.68 |  25.85 |    7.51 | 21-ea |
| JavaFloatParser  byte[]     | 726.46 |  2.8 % |     41.69 |  23.98 |    8.09 | 21-ea |
| JavaBigDecimalParser String | 536.22 | 18.5 % |     30.77 |  32.49 |    3.15 | 21-ea |
| JavaBigDecimalParser char[] | 623.72 | 14.8 % |     35.80 |  27.94 |    3.67 | 21-ea |
| JavaBigDecimalParser byte[] | 599.53 | 12.7 % |     34.41 |  29.06 |    3.53 | 21-ea |

MacBook Pro (2023),
CPU: Apple M2 Max,
OS: Mac OS X, 13.4.1, 12 processors available

| Method                      |    MB/s | stdev | Mfloats/s |   ns/f | speedup | JDK   |
|-----------------------------|--------:|------:|----------:|-------:|--------:|-------|
| java.lang.Double            |  117.09 | 3.9 % |      6.72 | 148.79 |    1.00 | 21-ea |
| java.lang.Float             |  109.61 | 8.5 % |      6.29 | 158.95 |    1.00 | 21-ea |
| java.math.BigDecimal        |  255.19 | 4.1 % |     14.65 |  68.27 |    1.00 | 21-ea |
| JavaDoubleParser String     |  626.50 | 3.5 % |     35.96 |  27.81 |    5.35 | 21-ea |
| JavaDoubleParser char[]     |  931.24 | 3.1 % |     53.45 |  18.71 |    7.95 | 21-ea |
| JavaDoubleParser byte[]     |  970.42 | 1.8 % |     55.70 |  17.95 |    8.29 | 21-ea |
| JsonDoubleParser String     |  649.27 | 4.1 % |     37.27 |  26.83 |    5.54 | 21-ea |
| JsonDoubleParser char[]     | 1017.07 | 3.8 % |     58.38 |  17.13 |    8.69 | 21-ea |
| JsonDoubleParser byte[]     | 1011.07 | 2.3 % |     58.03 |  17.23 |    8.63 | 21-ea |
| JavaFloatParser  String     |  667.52 | 4.1 % |     38.31 |  26.10 |    6.09 | 21-ea |
| JavaFloatParser  char[]     |  973.80 | 2.6 % |     55.89 |  17.89 |    8.88 | 21-ea |
| JavaFloatParser  byte[]     | 1006.72 | 2.2 % |     57.78 |  17.31 |    9.18 | 21-ea |
| JavaBigDecimalParser String |  872.42 | 3.7 % |     50.08 |  19.97 |    3.42 | 21-ea |
| JavaBigDecimalParser char[] | 1023.17 | 5.8 % |     58.73 |  17.03 |    4.01 | 21-ea |
| JavaBigDecimalParser byte[] | 1015.23 | 4.3 % |     58.27 |  17.16 |    3.98 | 21-ea |

### The data file `canada.txt`

This file contains numbers in the range from -128 to +128.
Most input lines look like this: `52.038048000000117`.

Mac Mini (2018)
CPU: Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz
OS: Mac OS X, 13.4.1, 12 processors available

| Method                      |   MB/s |  stdev | Mfloats/s |   ns/f | speedup | JDK |
|-----------------------------|-------:|-------:|----------:|-------:|--------:|-----|
| java.lang.Double            |  85.65 |  6.0 % |      4.92 | 203.18 |    1.00 | 20  |
| java.lang.Float             |  90.42 |  4.3 % |      5.20 | 192.45 |    1.00 | 20  |
| java.math.BigDecimal        | 300.85 | 12.9 % |     17.29 |  57.84 |    1.00 | 20  |
| JavaDoubleParser String     | 369.51 | 13.4 % |     21.23 |  47.09 |    4.31 | 20  |
| JavaDoubleParser char[]     | 549.31 | 15.3 % |     31.57 |  31.68 |    6.41 | 20  |
| JavaDoubleParser byte[]     | 688.91 |  3.0 % |     39.59 |  25.26 |    8.04 | 20  |
| JsonDoubleParser String     | 385.61 | 14.8 % |     22.16 |  45.13 |    4.50 | 20  |
| JsonDoubleParser char[]     | 571.06 |  3.9 % |     32.82 |  30.47 |    6.67 | 20  |
| JsonDoubleParser byte[]     | 700.08 |  3.6 % |     40.23 |  24.86 |    8.17 | 20  |
| JavaFloatParser  String     | 379.28 | 14.0 % |     21.80 |  45.88 |    4.19 | 20  |
| JavaFloatParser  char[]     | 655.42 |  4.9 % |     37.66 |  26.55 |    7.25 | 20  |
| JavaFloatParser  byte[]     | 578.16 | 13.5 % |     33.23 |  30.10 |    6.39 | 20  |
| JavaBigDecimalParser String | 406.57 | 15.5 % |     23.36 |  42.80 |    1.35 | 20  |
| JavaBigDecimalParser char[] | 628.74 | 18.9 % |     36.13 |  27.68 |    2.09 | 20  |
| JavaBigDecimalParser byte[] | 687.52 |  5.9 % |     39.51 |  25.31 |    2.29 | 20  |

MacBook Pro (2023)
CPU: Apple M2 Max
OS: Mac OS X, 13.4.1, 12 processors available

| Method                      |    MB/s | stdev | Mfloats/s |   ns/f | speedup | JDK   |
|-----------------------------|--------:|------:|----------:|-------:|--------:|-------|
| java.lang.Double            |   98.31 | 1.0 % |      5.65 | 177.00 |    1.00 | 21-ea |
| java.lang.Float             |  108.96 | 1.4 % |      6.26 | 159.71 |    1.00 | 21-ea |
| java.math.BigDecimal        |  378.73 | 2.8 % |     21.76 |  45.95 |    1.00 | 21-ea |
| JavaDoubleParser String     |  548.16 | 1.6 % |     31.50 |  31.75 |    5.58 | 21-ea |
| JavaDoubleParser char[]     |  930.17 | 2.8 % |     53.45 |  18.71 |    9.46 | 21-ea |
| JavaDoubleParser byte[]     |  900.13 | 2.3 % |     51.73 |  19.33 |    9.16 | 21-ea |
| JsonDoubleParser String     |  546.09 | 3.7 % |     31.38 |  31.87 |    5.55 | 21-ea |
| JsonDoubleParser char[]     |  814.65 | 3.2 % |     46.82 |  21.36 |    8.29 | 21-ea |
| JsonDoubleParser byte[]     |  951.72 | 2.5 % |     54.69 |  18.28 |    9.68 | 21-ea |
| JavaFloatParser  String     |  556.85 | 3.9 % |     32.00 |  31.25 |    5.11 | 21-ea |
| JavaFloatParser  char[]     |  962.34 | 2.4 % |     55.30 |  18.08 |    8.83 | 21-ea |
| JavaFloatParser  byte[]     |  961.54 | 2.2 % |     55.26 |  18.10 |    8.82 | 21-ea |
| JavaBigDecimalParser String |  796.59 | 2.8 % |     45.78 |  21.84 |    2.10 | 21-ea |
| JavaBigDecimalParser char[] | 1157.88 | 5.0 % |     66.54 |  15.03 |    3.06 | 21-ea |
| JavaBigDecimalParser byte[] | 1122.34 | 6.6 % |     64.50 |  15.50 |    2.96 | 21-ea |

### The data file `mesh.txt`

This file contains input lines like `1749`, and `0.539081215858`.

Mac Mini (2018)
CPU: Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz
OS: Mac OS X, 13.4.1, 12 processors available

| Method                      |   MB/s |  stdev | Mfloats/s |  ns/f | speedup | JDK |
|-----------------------------|-------:|-------:|----------:|------:|--------:|-----|
| java.lang.Double            | 160.78 | 21.1 % |     21.90 | 45.66 |    1.00 | 20  |
| java.lang.Float             |  83.57 | 15.8 % |     11.38 | 87.84 |    1.00 | 20  |
| java.math.BigDecimal        | 167.68 | 25.0 % |     22.84 | 43.78 |    1.00 | 20  |
| JavaDoubleParser String     | 224.29 | 28.1 % |     30.55 | 32.73 |    1.40 | 20  |
| JavaDoubleParser char[]     | 332.82 | 38.2 % |     45.34 | 22.06 |    2.07 | 20  |
| JavaDoubleParser byte[]     | 381.87 | 39.7 % |     52.02 | 19.22 |    2.38 | 20  |
| JsonDoubleParser String     | 231.77 | 38.3 % |     31.57 | 31.67 |    1.44 | 20  |
| JsonDoubleParser char[]     | 359.96 | 38.8 % |     49.04 | 20.39 |    2.24 | 20  |
| JsonDoubleParser byte[]     | 415.99 |  4.5 % |     56.67 | 17.65 |    2.59 | 20  |
| JavaFloatParser  String     | 244.35 | 20.3 % |     33.29 | 30.04 |    2.92 | 20  |
| JavaFloatParser  char[]     | 378.61 |  4.4 % |     51.58 | 19.39 |    4.53 | 20  |
| JavaFloatParser  byte[]     | 421.33 | 20.8 % |     57.40 | 17.42 |    5.04 | 20  |
| JavaBigDecimalParser String | 296.23 | 21.6 % |     40.36 | 24.78 |    1.77 | 20  |
| JavaBigDecimalParser char[] | 420.40 | 25.3 % |     57.27 | 17.46 |    2.51 | 20  |
| JavaBigDecimalParser byte[] | 413.61 |  3.9 % |     56.34 | 17.75 |    2.47 | 20  |

MacBook Pro (2023)
CPU: Apple M2 Max
OS: Mac OS X, 13.4.1, 12 processors available

| Method                      |   MB/s | stdev | Mfloats/s |  ns/f | speedup | JDK   |
|-----------------------------|-------:|------:|----------:|------:|--------:|-------|
| java.lang.Double            | 290.80 | 4.1 % |     39.61 | 25.24 |    1.00 | 21-ea |
| java.lang.Float             | 120.65 | 2.5 % |     16.44 | 60.84 |    1.00 | 21-ea |
| java.math.BigDecimal        | 274.28 | 7.2 % |     37.36 | 26.76 |    1.00 | 21-ea |
| JavaDoubleParser String     | 473.30 | 2.0 % |     64.48 | 15.51 |    1.63 | 21-ea |
| JavaDoubleParser char[]     | 684.24 | 4.3 % |     93.21 | 10.73 |    2.35 | 21-ea |
| JavaDoubleParser byte[]     | 691.81 | 3.9 % |     94.24 | 10.61 |    2.38 | 21-ea |
| JsonDoubleParser String     | 491.06 | 2.6 % |     66.90 | 14.95 |    1.69 | 21-ea |
| JsonDoubleParser char[]     | 703.86 | 2.2 % |     95.88 | 10.43 |    2.42 | 21-ea |
| JsonDoubleParser byte[]     | 732.64 | 1.9 % |     99.81 | 10.02 |    2.52 | 21-ea |
| JavaFloatParser  String     | 454.21 | 4.4 % |     61.88 | 16.16 |    3.76 | 21-ea |
| JavaFloatParser  char[]     | 625.43 | 3.4 % |     85.20 | 11.74 |    5.18 | 21-ea |
| JavaFloatParser  byte[]     | 546.98 | 2.4 % |     74.51 | 13.42 |    4.53 | 21-ea |
| JavaBigDecimalParser String | 557.09 | 6.9 % |     75.89 | 13.18 |    2.03 | 21-ea |
| JavaBigDecimalParser char[] | 765.37 | 3.6 % |    104.26 |  9.59 |    2.79 | 21-ea |
| JavaBigDecimalParser byte[] | 745.62 | 3.1 % |    101.57 |  9.85 |    2.72 | 21-ea |

### The data file `canada_hex.txt`

This file contains numbers in the range from -128 to +128 in hexadecimal notation.
Most input lines look like this: `-0x1.09219008205fcp6`.

|Method                     | MB/s  |stdev|Mfloats/s| ns/f   | speedup | JDK    |
|---------------------------|------:|-----:|------:|--------:|--------:|--------|
|java.lang.Double           |  37.68| 5.2 %|   2.07|   484.07|     1.00|20      |
|java.lang.Float            |  37.78| 4.4 %|   2.07|   482.70|     1.00|20      |
|JavaDoubleParser String    | 394.05|14.3 %|  21.61|    46.28|    10.46|20      |
|JavaDoubleParser char[]    | 563.86|16.1 %|  30.92|    32.35|    14.97|20      |
|JavaDoubleParser byte[]    | 705.25| 2.7 %|  38.67|    25.86|    18.72|20      |
|JavaFloatParser  String    | 399.74|14.8 %|  21.92|    45.62|    10.58|20      |
|JavaFloatParser  char[]    | 601.32| 4.3 %|  32.97|    30.33|    15.91|20      |
|JavaFloatParser  byte[]    | 571.88|12.4 %|  31.36|    31.89|    15.14|20      |

### Comparison with C version

For comparison. here are the test results
of [simple_fastfloat_benchmark](https://github.com/lemire/simple_fastfloat_benchmark)  
on the same computer:

    version: Thu Mar 31 10:18:12 2022 -0400 f2082bf747eabc0873f2fdceb05f9451931b96dc

    Intel(R) Core(TM) i7-8700B CPU @ 3.20GHz SIMD-256

    $ ./build/benchmarks/benchmark
    # parsing random numbers
    available models (-m): uniform one_over_rand32 simple_uniform32 simple_int32 int_e_int simple_int64 bigint_int_dot_int big_ints 
    model: generate random numbers uniformly in the interval [0.0.1.0]
    volume: 100000 floats
    volume = 2.09808 MB 
    netlib                      :   317.31 MB/s (+/- 6.0 %)    15.12 Mfloat/s      66.12 ns/f 
    doubleconversion            :   263.89 MB/s (+/- 4.2 %)    12.58 Mfloat/s      79.51 ns/f 
    strtod                      :    86.13 MB/s (+/- 3.7 %)     4.10 Mfloat/s     243.61 ns/f 
    abseil                      :   467.27 MB/s (+/- 9.0 %)    22.27 Mfloat/s      44.90 ns/f 
    fastfloat                   :   880.79 MB/s (+/- 6.6 %)    41.98 Mfloat/s      23.82 ns/f 

    OpenJDK 20+36-2344
    java.lang.Double            :    93.97 MB/s (+/- 5.0 %)     5.39 Mfloat/s     185.43 ns/f     1.00 speedup
    JavaDoubleParser String     :   534.52 MB/s (+/-11.2 %)    30.67 Mfloat/s      32.60 ns/f     5.69 speedup
    JavaDoubleParser char[]     :   620.86 MB/s (+/- 9.9 %)    35.63 Mfloat/s      28.07 ns/f     6.61 speedup
    JavaDoubleParser byte[]     :   724.91 MB/s (+/- 5.7 %)    41.60 Mfloat/s      24.04 ns/f     7.71 speedup

'

    $ ./build/benchmarks/benchmark -f data/canada.txt
    # read 111126 lines 
    volume = 1.93374 MB 
    netlib                      :   337.79 MB/s (+/- 5.8 %)    19.41 Mfloat/s      51.52 ns/f 
    doubleconversion            :   254.22 MB/s (+/- 6.0 %)    14.61 Mfloat/s      68.45 ns/f 
    strtod                      :    73.33 MB/s (+/- 7.1 %)     4.21 Mfloat/s     237.31 ns/f 
    abseil                      :   411.11 MB/s (+/- 7.3 %)    23.63 Mfloat/s      42.33 ns/f 
    fastfloat                   :   741.32 MB/s (+/- 5.3 %)    42.60 Mfloat/s      23.47 ns/f 

    OpenJDK 20+36-2344
    java.lang.Double            :    82.56 MB/s (+/- 4.4 %)     4.74 Mfloat/s     210.76 ns/f     1.00 speedup
    JavaDoubleParser String     :   366.27 MB/s (+/- 9.7 %)    21.05 Mfloat/s      47.51 ns/f     4.44 speedup
    JavaDoubleParser char[]     :   571.76 MB/s (+/-11.4 %)    32.86 Mfloat/s      30.43 ns/f     6.93 speedup
    JavaDoubleParser byte[]     :   622.03 MB/s (+/- 7.5 %)    35.75 Mfloat/s      27.98 ns/f     7.53 speedup

# Building and running the code

This project requires **at least** the items below to build it from source:

- Maven 3.8.6
- OpenJDK SE 20

This project contains optimised code for various JDK versions.
If you intend to assess the fitness and/or performance of this project for all supported
JDKs, you **also need** to install the following items:

- OpenJDK SE 8
- OpenJDK SE 11
- OpenJDK SE 17
- OpenJDK SE 19

When you clone the code repository from github. you can choose from the following branches:

- `main` Aims to contain only working code.
- `dev` This code may or may not work. This code uses the experimental Vector API, and the Foreign Memory Access API,
  that are included in Java 20.

## Command sequence with Java SE 20 on macOS:

```shell
git clone https://github.com/wrandelshofer/FastDoubleParser.git
cd FastDoubleParser 
javac --enable-preview -source 20 -d out -encoding utf8 --module-source-path fastdoubleparser-dev/src/main/java --module ch.randelshofer.fastdoubleparser    
javac --enable-preview -source 20 -d out -encoding utf8 -p out --module-source-path fastdoubleparserdemo-dev/src/main/java --module ch.randelshofer.fastdoubleparserdemo
java -XX:CompileCommand=inline,java/lang/String.charAt --enable-preview -p out -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main  
java -XX:CompileCommand=inline,java/lang/String.charAt --enable-preview -p out -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main FastDoubleParserDemo/data/canada.txt   
```

## Command sequence with Java SE 8, 11, 17, 19 and 20 and Maven 3.8.6 on macOS:

```shell
git clone https://github.com/wrandelshofer/FastDoubleParser.git
cd FastDoubleParser
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home 
mvn clean
mvn package
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home 
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/mesh.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada_hex.txt
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-19.jdk/Contents/Home 
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/mesh.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada_hex.txt
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home 
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/mesh.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada_hex.txt
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.0.8.jdk/Contents/Home
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/mesh.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -p fastdoubleparser/target:fastdoubleparserdemo/target -m ch.randelshofer.fastdoubleparserdemo/ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada_hex.txt
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_281.jdk/Contents/Home
java -XX:CompileCommand=inline,java/lang/String.charAt -cp "fastdoubleparser/target/*:fastdoubleparserdemo/target/*" ch.randelshofer.fastdoubleparserdemo.Main --markdown
java -XX:CompileCommand=inline,java/lang/String.charAt -cp "fastdoubleparser/target/*:fastdoubleparserdemo/target/*" ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -cp "fastdoubleparser/target/*:fastdoubleparserdemo/target/*" ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/mesh.txt
java -XX:CompileCommand=inline,java/lang/String.charAt -cp "fastdoubleparser/target/*:fastdoubleparserdemo/target/*" ch.randelshofer.fastdoubleparserdemo.Main --markdown FastDoubleParserDemo/data/canada_hex.txt
```

## IntelliJ IDEA with Java SE 8, 11, 17, 19 and 20 on macOS

Prerequisites:

1. Install the following Java SDKs: 8, 11, 17, 19 and 20.
   _If you do not need to edit the code, you only need to install the Java 20 SDK._
2. Install IntelliJ IDEA

Steps:

1. Start IntelliJ IDEA
2. From the main menu, choose **Git > Clone...**
3. In the dialog that opens, enter the URL https://github.com/wrandelshofer/FastDoubleParser.git,
   specify the directory in which you want to save the project and click **Clone**.
4. Intellij IDEA will now clone the repository and open a new project window.
   However, the project modules are not yet configured correctly.
5. From the main menu of the new project window, choose **View > Tool Windows > Maven**
6. In the Maven tool window, run the Maven build **Parent project for FastDoubleParser > Lifecycle > compile**
7. In the toolbar of the Maven tool window, click **Reload All Maven Projects**
8. Intellij IDEA knows now for each module, where the **source**,
   **generated source**,  **test source**, and **generated test source** folders are.
   However, the project modules have still incorrect JDK dependencies.
9. _You can skip this step, if you do not want to edit the code._
   From the main menu, choose **File > Project Structure...**
10. _You can skip this step, if you do not want to edit the code._
    In the dialog that opens, select in the navigation bar **Project Settings > Modules**
11. _You can skip this step, if you do not want to edit the code._
    For each module in the right pane of the dialog, select the **Dependencies** tab.
    Specify the corresponding **Module SDK** for modules which have a name that ends in
    **-Java8**, **-Java11**, **-Java17**, **-Java19**.
    Do not change modules with other name endings - they must stay on the Java 20 SDK.

12. From the main menu, choose **Build > Build Project**
    Intellij IDEA will now properly build the project.

## Editing the code

The majority of the code is located in the module named **fastdoubleparser-dev**,
and **fastdoubleparserdemo-dev**.
The code in these modules uses early access features of the Java 20 SDK.

Modules which have a name that ends in **-Java8**, **-Java11**, **-Java17**, **-Java19**
contain deltas of the **-dev** modules.

The delta code is located in the **source** and **test** folders of the module.
Code from the **-dev** module is located in the **generated source** and
**generated test source** folders.

The Maven POM of a module contains **maven-resources-plugin** elements that copy code
from the **-dev** module to the delta modules.

## Testing the code

Unfortunately it is not possible to test floating parsers exhaustively, because the input
and output spaces are far too big.

| Parser               | Input Space                                                                                     | Output Space                                                                            |
|----------------------|-------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| JavaDoubleParser     | 1 to 2<sup>31</sup>-1 chars<br>= 65536<sup>2<sup>31</sup></sup><br>= 2<sup>34,359,738,368</sup> | 64 bits<br>= 2<sup>64</sup>                                                             |
| JavaFloatParser      | 1 to 2<sup>31</sup>-1 chars<br>= 2<sup>34,359,738,368</sup>                                     | 32 bits<br>= 2<sup>32</sup>                                                             |
| JsonDoubleParser     | 1 to 2<sup>31</sup>-1 chars<br>= 2<sup>34,359,738,368</sup>                                     | 64 bits<br>= 2<sup>64</sup>                                                             |
| JavaBigIntegerParser | 1 to 1,292,782,622 chars<br>= 65536<sup>1292782623</sup><br>= 2<sup>20,684,521,968</sup>        | 0 to 2<sup>31</sup> bits<br>= 2<sup>2<sup>31</sup></sup><br>= 2<sup>2,147,483,648</sup> |
| JavaBigDecimalParser | 1 to 1,292,782,635 chars<br>= 65536<sup>1292782636</sup><br>= 2<sup>20,684,522,176</sup>        | 0 to 2<sup>31</sup> bit mantissa * 64 bit exponent<br>= 2<sup>12,884,901,888</sup>      |

You can quickly run a number of hand-picked tests that aim for 100 % line coverage:

```
mvn -DenableLongRunningTests=true test
```

You can run additional tests with the following command. The purpose of these tests is to explore additional
regions of the input and output spaces.

```
mvn -DenableLongRunningTests=true test
```