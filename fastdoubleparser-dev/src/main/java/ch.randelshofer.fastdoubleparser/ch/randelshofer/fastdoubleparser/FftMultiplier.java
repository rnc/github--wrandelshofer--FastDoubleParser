/*
 * @(#)FftMultiplier.java
 * Copyright © 2022 Werner Randelshofer, Switzerland. MIT License.
 */
package ch.randelshofer.fastdoubleparser;

import java.math.BigInteger;
import java.util.Arrays;

import static ch.randelshofer.fastdoubleparser.FastIntegerMath.getMagnitude;
import static ch.randelshofer.fastdoubleparser.FastIntegerMath.newBigInteger;

/**
 * Provides methods for multiplying two {@link BigInteger}s using the
 * {@code FFT algorithm}.
 * <p>
 * This code is based on {@code bigint} by Timothy Buktu.
 * <p>
 * References:
 * <dt>bigint, Copyright 2013 Timothy Buktu, 2-Clause BSD License.
 * </dt>
 * <dd><a href="https://github.com/tbuktu/bigint/tree/floatfft">github.com</a></dd>
 * </dl>
 */
class FftMultiplier {
    /**
     * The threshold value for using 3-way Toom-Cook multiplication.
     */
    private static final int TOOM_COOK_THRESHOLD = 240 * 8;
    /**
     * The threshold value for using floating point FFT multiplication.
     * If the number of bits in each mag array is greater than the
     * Toom-Cook threshold, and the number of bits in at least one of
     * the mag arrays is greater than this threshold, then FFT
     * multiplication will be used.
     */
    private static final int FFT_THRESHOLD = 3400 * 8;

    /**
     * Returns a BigInteger whose value is {@code (this<sup>2</sup>)}.
     *
     * @return {@code this<sup>2</sup>}
     */
    static BigInteger square(BigInteger a) {
        return square(a, false);
    }

    /**
     * Returns a BigInteger whose value is {@code (this<sup>2</sup>)}. If
     * the invocation is recursive certain overflow checks are skipped.
     *
     * @param isRecursion whether this is a recursive invocation
     * @return {@code this<sup>2</sup>}
     */
    static BigInteger square(BigInteger a, boolean isRecursion) {
        if (a.signum() == 0) {
            return BigInteger.ZERO;
        }
        int len = a.bitLength();

        if (len >>> 3 < FFT_THRESHOLD) {
            return a.multiply(a);
        } else {
            return squareFFT(a);
        }
    }

    private static BigInteger squareFFT(BigInteger a) {
        int[] mag = getMagnitude(a);
        int bitLen = mag.length * 32;
        int bitsPerPoint = bitsPerFFTPoint(bitLen);
        int fftLen = (bitLen + bitsPerPoint - 1) / bitsPerPoint + 1;   // +1 for a possible carry, see toFFTVector()
        int logFFTLen = 32 - Integer.numberOfLeadingZeros(fftLen - 1);

        // Use a 2^n or 3*2^n transform, whichever is shorter
        int fftLen2 = 1 << (logFFTLen);   // rounded to 2^n
        int fftLen3 = fftLen2 * 3 / 4;   // rounded to 3*2^n
        if (fftLen < fftLen3) {
            fftLen = fftLen3;
            MutableComplex[] vec = toFFTVector(mag, fftLen, bitsPerPoint);
            MutableComplex[][] roots2 = getRootsOfUnity2(logFFTLen - 2);   // roots for length fftLen/3 which is a power of two
            MutableComplex[] weights = getRootsOfUnity3(logFFTLen - 2);
            MutableComplex[] twiddles = getRootsOfUnity3(logFFTLen - 4);
            applyWeights(vec, weights);
            fftMixedRadix(vec, roots2, twiddles);
            squarePointwise(vec);
            ifftMixedRadix(vec, roots2, twiddles);
            applyInverseWeights(vec, weights);
            BigInteger c = fromFFTVector(vec, 1, bitsPerPoint);
            return c;
        } else {
            fftLen = fftLen2;
            MutableComplex[] vec = toFFTVector(mag, fftLen, bitsPerPoint);
            MutableComplex[][] roots = getRootsOfUnity2(logFFTLen);
            applyWeights(vec, roots[logFFTLen]);
            fft(vec, roots);
            squarePointwise(vec);
            ifft(vec, roots);
            applyInverseWeights(vec, roots[logFFTLen]);
            BigInteger c = fromFFTVector(vec, 1, bitsPerPoint);
            return c;
        }
    }

    // Converts this BigInteger into an array of complex numbers suitable for an FFT.
    // Populates the real parts and sets the imaginary parts to zero.
    private static MutableComplex[] toFFTVector(int[] mag, int fftLen, int bitsPerFFTPoint) {
        MutableComplex[] fftVec = new MutableComplex[fftLen];
        int fftIdx = 0;
        int magBitIdx = 0;   // next bit of the current mag element
        int magIdx = mag.length - 1;
        int carry = 0;   // when we subtract base from a digit, we need to carry one
        int base = 1 << bitsPerFFTPoint;
        while (magIdx >= 0) {
            int fftPoint = 0;
            int fftBitIdx = 0;
            do {
                int bitsToCopy = Math.min(32 - magBitIdx, bitsPerFFTPoint - fftBitIdx);
                fftPoint |= ((mag[magIdx] >> magBitIdx) & ((1 << bitsToCopy) - 1)) << fftBitIdx;
                fftBitIdx += bitsToCopy;
                magBitIdx += bitsToCopy;
                if (magBitIdx >= 32) {
                    magBitIdx = 0;
                    magIdx--;
                    if (magIdx < 0) {
                        break;
                    }
                }
            } while (fftBitIdx < bitsPerFFTPoint);

            // "balance" the output digits so -base/2 < digit < base/2
            fftPoint += carry;
            if (fftPoint > base / 2) {
                fftPoint -= base;
                carry = 1;
            } else {
                carry = 0;
            }

            fftVec[fftIdx] = new MutableComplex(fftPoint, 0);
            fftIdx++;
        }
        // final carry
        if (carry > 0) {
            fftVec[fftIdx] = new MutableComplex(carry, 0);
            fftIdx++;
        }
        while (fftIdx < fftLen)
            fftVec[fftIdx++] = new MutableComplex(0, 0);
        return fftVec;
    }

    // Converts an array of complex numbers back into a BigInteger.
    // Expects the real parts to contain the lower half and the imaginary
    // parts to contain the upper half of the result.
    private static BigInteger fromFFTVector(MutableComplex[] fftVec, int signum, int bitsPerFFTPoint) {
        int fftLen = fftVec.length;
        long magLen = 2 * ((long) fftLen * bitsPerFFTPoint + 31) / 32;
        int[] mag = new int[(int) Math.min(magLen, Integer.MAX_VALUE - 4)];
        int magIdx = mag.length - 1;
        int magBitIdx = 0;
        long carry = 0;
        for (int part = 0; part <= 1; part++) {   // 0=real, 1=imaginary
            int fftIdx = 0;
            while (fftIdx < fftLen) {
                int fftBitIdx = 0;
                long fftElem = Math.round(part == 0 ? fftVec[fftIdx].real : fftVec[fftIdx].imag) + carry;
                carry = fftElem >> bitsPerFFTPoint;
                fftElem &= (1 << bitsPerFFTPoint) - 1;
                do {
                    int bitsToCopy = Math.min(32 - magBitIdx, bitsPerFFTPoint - fftBitIdx);
                    mag[magIdx] |= (fftElem >> fftBitIdx) << magBitIdx;
                    magBitIdx += bitsToCopy;
                    fftBitIdx += bitsToCopy;
                    if (magBitIdx >= 32) {
                        magBitIdx = 0;
                        magIdx--;
                    }
                } while (fftBitIdx < bitsPerFFTPoint);
                fftIdx++;
            }
        }
        return newBigInteger(signum, mag);
    }

    // Multiplies the elements of an FFT vector by weights.
    // Doing this makes a regular FFT convolution a right-angle convolution.
    private static void applyWeights(MutableComplex[] a, MutableComplex[] weights) {
        for (int i = 0; i < a.length; i++)
            a[i].multiply(weights[i]);   // possible optimization: use the fact that a[i].imag == 0
    }

    // Multiplies the elements of an FFT vector by 1/weight.
    // Used for the right-angle convolution.
    private static void applyInverseWeights(MutableComplex[] a, MutableComplex[] weights) {
        for (int i = 0; i < a.length; i++)
            a[i].multiplyConjugate(weights[i]);
    }

    /**
     * Returns the maximum number of bits that one double precision number can fit without
     * causing the multiplication to be incorrect.
     *
     * @param bitLen length of this number in bits
     * @return
     */
    private static int bitsPerFFTPoint(int bitLen) {
        if (bitLen <= 19 * (1 << 9)) {
            return 19;
        }
        if (bitLen <= 18 * (1 << 10)) {
            return 18;
        }
        if (bitLen <= 17 * (1 << 12)) {
            return 17;
        }
        if (bitLen <= 16 * (1 << 14)) {
            return 16;
        }
        if (bitLen <= 15 * (1 << 16)) {
            return 15;
        }
        if (bitLen <= 14 * (1 << 18)) {
            return 14;
        }
        if (bitLen <= 13 * (1 << 20)) {
            return 13;
        }
        if (bitLen <= 12 * (1 << 21)) {
            return 12;
        }
        if (bitLen <= 11 * (1 << 23)) {
            return 11;
        }
        if (bitLen <= 10 * (1 << 25)) {
            return 10;
        }
        if (bitLen <= 9 * (1 << 27)) {
            return 9;
        }
        return 8;
    }

    /**
     * Returns sets of complex roots of unity. For k=logN, logN-2, logN-4, ...,
     * the return value contains all k-th roots between 0 and pi/2.
     *
     * @param logN for a transform of length 2^logN
     * @return
     */
    private static MutableComplex[][] getRootsOfUnity2(int logN) {
        MutableComplex[][] roots = new MutableComplex[logN + 1][];
        for (int i = logN; i >= 0; i -= 2) {
            if (i < ROOTS_CACHE2_SIZE) {
                if (ROOTS2_CACHE[i] == null) {
                    ROOTS2_CACHE[i] = calculateRootsOfUnity(1 << i);
                }
                roots[i] = ROOTS2_CACHE[i];
            } else {
                roots[i] = calculateRootsOfUnity(1 << i);
            }
        }
        return roots;
    }

    /**
     * Returns sets of complex roots of unity. For k=logN, logN-2, logN-4, ...,
     * the return value contains all k-th roots between 0 and pi/2.
     *
     * @param logN for a transform of length 3*2^logN
     * @return
     */
    private static MutableComplex[] getRootsOfUnity3(int logN) {
        if (logN < ROOTS3_CACHE_SIZE) {
            if (ROOTS3_CACHE[logN] == null) {
                ROOTS3_CACHE[logN] = calculateRootsOfUnity(3 << logN);
            }
            return ROOTS3_CACHE[logN];
        } else {
            return calculateRootsOfUnity(3 << logN);
        }
    }

    private static class MutableComplex {
        double real, imag;

        MutableComplex(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }

        void copyTo(MutableComplex c) {
            c.real = real;
            c.imag = imag;
        }

        void add(MutableComplex c) {
            real += c.real;
            imag += c.imag;
        }

        void add(MutableComplex c, MutableComplex destination) {
            destination.real = real + c.real;
            destination.imag = imag + c.imag;
        }

        void subtract(MutableComplex c) {
            real -= c.real;
            imag -= c.imag;
        }

        void subtract(MutableComplex c, MutableComplex destination) {
            destination.real = real - c.real;
            destination.imag = imag - c.imag;
        }

        void multiply(MutableComplex c) {
            double temp = real;
            real = real * c.real - imag * c.imag;
            imag = temp * c.imag + imag * c.real;
        }

        void multiply(MutableComplex c, MutableComplex destination) {
            destination.real = real * c.real - imag * c.imag;
            destination.imag = real * c.imag + imag * c.real;
        }

        // multiplies this number by the conjugate of c.
        void multiplyConjugate(MutableComplex c) {
            double temp = real;
            real = real * c.real + imag * c.imag;
            imag = -temp * c.imag + imag * c.real;
        }

        // Multiplies this number by the conjugate of c and puts the result into destination.
        // Leaves this number unmodified.
        void multiplyConjugate(MutableComplex c, MutableComplex destination) {
            destination.real = real * c.real + imag * c.imag;
            destination.imag = -real * c.imag + imag * c.real;
        }

        // Multiplies this number by the conjugate of c and by i.
        void multiplyConjugateTimesI(MutableComplex c) {
            double temp = real;
            real = -real * c.imag + imag * c.real;
            imag = -temp * c.real - imag * c.imag;
        }

        // Multiplies this number by c and by i.
        void multiplyByIAnd(MutableComplex c) {
            double temp = real;
            real = -real * c.imag - imag * c.real;
            imag = temp * c.real - imag * c.imag;
        }

        // Adds c*i to this number.
        void addTimesI(MutableComplex c) {
            real -= c.imag;
            imag += c.real;
        }

        // Adds c*i to this number. Leaves this number unmodified.
        void addTimesI(MutableComplex c, MutableComplex destination) {
            destination.real = real - c.imag;
            destination.imag = imag + c.real;
        }

        void subtractTimesI(MutableComplex c) {
            real += c.imag;
            imag -= c.real;
        }

        void subtractTimesI(MutableComplex c, MutableComplex destination) {
            destination.real = real + c.imag;
            destination.imag = imag - c.real;
        }

        void square() {
            double temp = real;
            real = real * real - imag * imag;
            imag = 2 * temp * imag;
        }

        void square(MutableComplex destination) {
            destination.real = real * real - imag * imag;
            destination.imag = 2 * real * imag;
        }

        void timesTwoToThe(int n) {
            real = Math.scalb(real, n);
            imag = Math.scalb(imag, n);
        }
    }

    /**
     * for FFTs of length up to 2^17
     */
    private static final int ROOTS_CACHE2_SIZE = 18;
    /**
     * for FFTs of length up to 3*2^15
     */
    private static final int ROOTS3_CACHE_SIZE = 15;
    /**
     * Sets of complex roots of unity. The set at index k contains 2^k
     * elements representing all (2^(k+2))-th roots between 0 and pi/2.
     * Used for FFT multiplication.
     */
    private volatile static MutableComplex[][] ROOTS2_CACHE = new MutableComplex[ROOTS_CACHE2_SIZE][];
    /**
     * Sets of complex roots of unity. The set at index k contains 3*2^k
     * elements representing all (3*2^(k+2))-th roots between 0 and pi/2.
     * Used for FFT multiplication.
     */
    private volatile static MutableComplex[][] ROOTS3_CACHE = new MutableComplex[ROOTS3_CACHE_SIZE][];

    /**
     * Performs an FFT of length 3*2^n on the vector {@code a}.
     * Uses the 4-step algorithm to decompose the 3*2^n FFT into 2^n FFTs of
     * length 3 and 3 FFTs of length 2^n.
     * See https://www.nas.nasa.gov/assets/pdf/techreports/1989/rnr-89-004.pdf
     *
     * @param a      input and output, must be 3*2^n in size for some n>=2
     * @param roots2 an array that contains one set of roots at indices
     *               log2(a.length/3), log2(a.length/3)-2, log2(a.length/3)-4, ...
     *               Each roots[s] must contain 2^s roots of unity such that
     *               {@code roots[s][k] = e^(pi*k*i/(2*roots.length))},
     *               i.e., they must cover the first quadrant.
     * @param roots3 must be the same length as {@code a} and contain roots of
     *               unity such that {@code roots[k] = e^(pi*k*i/(2*roots3.length))},
     *               i.e., they need to cover the first quadrant.
     */
    private static void fftMixedRadix(MutableComplex[] a, MutableComplex[][] roots2, MutableComplex[] roots3) {
        MutableComplex[] a0 = Arrays.copyOfRange(a, 0, a.length / 3);
        MutableComplex[] a1 = Arrays.copyOfRange(a, a.length / 3, a.length * 2 / 3);
        MutableComplex[] a2 = Arrays.copyOfRange(a, a.length * 2 / 3, a.length);

        // step 1: perform a.length/3 transforms of length 3
        fft3(a0, a1, a2, 1, 1);

        // step 2: multiply by roots of unity
        for (int i = 0; i < a.length / 4; i++) {
            MutableComplex omega = roots3[i];
            // a0[i] *= omega^0; a1[i] *= omega^1; a2[i] *= omega^2
            a1[i].multiplyConjugate(omega);
            a2[i].multiplyConjugate(omega);
            a2[i].multiplyConjugate(omega);
        }
        for (int i = a.length / 4; i < a.length / 3; i++) {
            MutableComplex omega = roots3[i - a.length / 4];
            // a0[i] *= omega^0; a1[i] *= omega^1; a2[i] *= omega^2
            a1[i].multiplyConjugateTimesI(omega);
            a2[i].multiplyConjugateTimesI(omega);
            a2[i].multiplyConjugateTimesI(omega);
        }

        // step 3 is not needed

        // step 4: perform 3 transforms of length a.length/3
        fft(a0, roots2);
        fft(a1, roots2);
        fft(a2, roots2);
    }

    /**
     * Performs an inverse FFT of length 3*2^n on the vector {@code a}.
     * Uses the 4-step algorithm to decompose the 3*2^n FFT into 2^n FFTs of
     * length 3 and 3 FFTs of length 2^n.
     * See https://www.nas.nasa.gov/assets/pdf/techreports/1989/rnr-89-004.pdf
     *
     * @param a      input and output, must be 3*2^n in size for some n>=2
     * @param roots2 an array that contains one set of roots at indices
     *               log2(a.length/3), log2(a.length/3)-2, log2(a.length/3)-4, ...
     *               Each roots[s] must contain 2^s roots of unity such that
     *               {@code roots[s][k] = e^(pi*k*i/(2*roots.length))},
     *               i.e., they must cover the first quadrant.
     * @param roots3 must be the same length as {@code a} and contain roots of
     *               unity such that {@code roots[k] = e^(pi*k*i/(2*roots3.length))},
     *               i.e., they need to cover the first quadrant.
     */
    private static void ifftMixedRadix(MutableComplex[] a, MutableComplex[][] roots2, MutableComplex[] roots3) {
        MutableComplex[] a0 = Arrays.copyOfRange(a, 0, a.length / 3);
        MutableComplex[] a1 = Arrays.copyOfRange(a, a.length / 3, a.length * 2 / 3);
        MutableComplex[] a2 = Arrays.copyOfRange(a, a.length * 2 / 3, a.length);

        // step 1: perform 3 transforms of length a.length/3
        ifft(a0, roots2);
        ifft(a1, roots2);
        ifft(a2, roots2);

        // step 2: multiply by roots of unity
        for (int i = 0; i < a.length / 4; i++) {
            MutableComplex omega = roots3[i];
            // a0[i] *= omega^0; a1[i] *= omega^1; a2[i] *= omega^2
            a1[i].multiply(omega);
            a2[i].multiply(omega);
            a2[i].multiply(omega);
        }
        for (int i = a.length / 4; i < a.length / 3; i++) {
            MutableComplex omega = roots3[i - a.length / 4];
            // a0[i] *= omega^0; a1[i] *= omega^1; a2[i] *= omega^2
            a1[i].multiplyByIAnd(omega);
            a2[i].multiplyByIAnd(omega);
            a2[i].multiplyByIAnd(omega);
        }

        // step 3 is not needed

        // step 4: perform a.length/3 transforms of length 3
        fft3(a0, a1, a2, -1, 1.0 / 3);
    }

    /**
     * Performs an FFT of length 2^n on the vector {@code a}.
     * This is a decimation-in-frequency implementation.
     *
     * @param a     input and output, must be a power of two in size
     * @param roots an array that contains one set of roots at indices
     *              log2(a.length), log2(a.length)-2, log2(a.length)-4, ...
     *              Each roots[s] must contain 2^s roots of unity such that
     *              {@code roots[s][k] = e^(pi*k*i/(2*roots.length))},
     *              i.e., they must cover the first quadrant.
     */
    private static void fft(MutableComplex[] a, MutableComplex[][] roots) {
        int n = a.length;
        int logN = 31 - Integer.numberOfLeadingZeros(n);
        MutableComplex a0 = new MutableComplex(0, 0);
        MutableComplex a1 = new MutableComplex(0, 0);
        MutableComplex a2 = new MutableComplex(0, 0);
        MutableComplex a3 = new MutableComplex(0, 0);

        // do two FFT stages at a time (radix-4)
        MutableComplex omega2 = new MutableComplex(0, 0);
        int s = logN;
        for (; s >= 2; s -= 2) {
            MutableComplex[] rootsS = roots[s - 2];
            int m = 1 << s;
            for (int i = 0; i < n; i += m) {
                for (int j = 0; j < m / 4; j++) {
                    MutableComplex omega1 = rootsS[j];
                    // computing omega2 from omega1 is less accurate than Math.cos() and Math.sin(),
                    // but it is the same error we'd incur with radix-2, so we're not breaking the
                    // assumptions of the Percival paper.
                    omega1.square(omega2);

                    int idx0 = i + j;
                    int idx1 = i + j + m / 4;
                    int idx2 = i + j + m / 2;
                    int idx3 = i + j + m * 3 / 4;

                    // radix-4 butterfly:
                    //   a[idx0] = (a[idx0] + a[idx1]      + a[idx2]      + a[idx3])      * w^0
                    //   a[idx1] = (a[idx0] + a[idx1]*(-i) + a[idx2]*(-1) + a[idx3]*i)    * w^1
                    //   a[idx2] = (a[idx0] + a[idx1]*(-1) + a[idx2]      + a[idx3]*(-1)) * w^2
                    //   a[idx3] = (a[idx0] + a[idx1]*i    + a[idx2]*(-1) + a[idx3]*(-i)) * w^3
                    // where w = omega1^(-1) = conjugate(omega1)
                    a[idx0].add(a[idx1], a0);
                    a0.add(a[idx2]);
                    a0.add(a[idx3]);

                    a[idx0].subtractTimesI(a[idx1], a1);
                    a1.subtract(a[idx2]);
                    a1.addTimesI(a[idx3]);
                    a1.multiplyConjugate(omega1);

                    a[idx0].subtract(a[idx1], a2);
                    a2.add(a[idx2]);
                    a2.subtract(a[idx3]);
                    a2.multiplyConjugate(omega2);

                    a[idx0].addTimesI(a[idx1], a3);
                    a3.subtract(a[idx2]);
                    a3.subtractTimesI(a[idx3]);
                    a3.multiply(omega1);   // Bernstein's trick: multiply by omega^(-1) instead of omega^3

                    a0.copyTo(a[idx0]);
                    a1.copyTo(a[idx1]);
                    a2.copyTo(a[idx2]);
                    a3.copyTo(a[idx3]);
                }
            }
        }

        // do one final radix-2 step if there is an odd number of stages
        if (s > 0) {
            for (int i = 0; i < n; i += 2) {
                // omega = 1
                a[i].copyTo(a0);
                a[i + 1].copyTo(a1);
                a[i].add(a1);
                a0.subtract(a1, a[i + 1]);
            }
        }
    }

    /**
     * Performs an inverse FFT of length 2^n on the vector {@code a}.
     * This is a decimation-in-time implementation.
     *
     * @param a     input and output, must be a power of two in size
     * @param roots an array that contains one set of roots at indices
     *              log2(a.length), log2(a.length)-2, log2(a.length)-4, ...
     *              Each roots[s] must contain 2^s roots of unity such that
     *              {@code roots[s][k] = e^(pi*k*i/(2*roots.length))},
     *              i.e., they must cover the first quadrant.
     */
    private static void ifft(MutableComplex[] a, MutableComplex[][] roots) {
        int n = a.length;
        int logN = 31 - Integer.numberOfLeadingZeros(n);
        MutableComplex a0 = new MutableComplex(0, 0);
        MutableComplex a1 = new MutableComplex(0, 0);
        MutableComplex a2 = new MutableComplex(0, 0);
        MutableComplex a3 = new MutableComplex(0, 0);
        MutableComplex b0 = new MutableComplex(0, 0);
        MutableComplex b1 = new MutableComplex(0, 0);
        MutableComplex b2 = new MutableComplex(0, 0);
        MutableComplex b3 = new MutableComplex(0, 0);

        int s = 1;
        // do one radix-2 step if there is an odd number of stages
        if (logN % 2 != 0) {
            for (int i = 0; i < n; i += 2) {
                // omega = 1
                a[i + 1].copyTo(a2);
                a[i].copyTo(a0);
                a[i].add(a2);
                a0.subtract(a2, a[i + 1]);
            }
            s++;
        }

        // do the remaining stages two at a time (radix-4)
        MutableComplex omega2 = new MutableComplex(0, 0);
        for (; s <= logN; s += 2) {
            MutableComplex[] rootsS = roots[s - 1];
            int m = 1 << (s + 1);
            for (int i = 0; i < n; i += m) {
                for (int j = 0; j < m / 4; j++) {
                    MutableComplex omega1 = rootsS[j];
                    // computing omega2 from omega1 is less accurate than Math.cos() and Math.sin(),
                    // but it is the same error we'd incur with radix-2, so we're not breaking the
                    // assumptions of the Percival paper.
                    omega1.square(omega2);

                    int idx0 = i + j;
                    int idx1 = i + j + m / 4;
                    int idx2 = i + j + m / 2;
                    int idx3 = i + j + m * 3 / 4;

                    // radix-4 butterfly:
                    //   a[idx0] = a[idx0]*w^0 + a[idx1]*w^1      + a[idx2]*w^2      + a[idx3]*w^3
                    //   a[idx1] = a[idx0]*w^0 + a[idx1]*i*w^1    + a[idx2]*(-1)*w^2 + a[idx3]*(-i)*w^3
                    //   a[idx2] = a[idx0]*w^0 + a[idx1]*(-1)*w^1 + a[idx2]*w^2      + a[idx3]*(-1)*w^3
                    //   a[idx3] = a[idx0]*w^0 + a[idx1]*(-i)*w^1 + a[idx2]*(-1)*w^2 + a[idx3]*i*w^3
                    // where w = omega1
                    a0 = a[idx0];
                    a[idx1].multiply(omega1, a1);
                    a[idx2].multiply(omega2, a2);
                    a[idx3].multiplyConjugate(omega1, a3);   // Bernstein's trick: multiply by omega^(-1) instead of omega^3

                    a0.add(a1, b0);
                    b0.add(a2);
                    b0.add(a3);

                    a0.addTimesI(a1, b1);
                    b1.subtract(a2);
                    b1.subtractTimesI(a3);

                    a0.subtract(a1, b2);
                    b2.add(a2);
                    b2.subtract(a3);

                    a0.subtractTimesI(a1, b3);
                    b3.subtract(a2);
                    b3.addTimesI(a3);

                    b0.copyTo(a[idx0]);
                    b1.copyTo(a[idx1]);
                    b2.copyTo(a[idx2]);
                    b3.copyTo(a[idx3]);
                }
            }
        }

        // divide all vector elements by n
        for (int i = 0; i < n; i++)
            a[i].timesTwoToThe(-logN);
    }

    /**
     * Performs FFTs or IFFTs of size 3 on the vector {@code (a0[i], a1[i], a2[i])}
     * for each {@code i}. The output is placed back into {@code a0, a1, and a2}.
     *
     * @param a0    inputs / outputs for the first FFT coefficient
     * @param a1    inputs / outputs for the second FFT coefficient
     * @param a2    inputs / outputs for the third FFT coefficient
     * @param sign  1 for a forward FFT, -1 for an inverse FFT
     * @param scale 1 for a forward FFT, 1/3 for an inverse FFT
     */
    private static void fft3(MutableComplex[] a0, MutableComplex[] a1, MutableComplex[] a2, int sign, double scale) {
        double omegaImag = sign * -0.5 * Math.sqrt(3);   // imaginary part of omega for n=3: sin(sign*(-2)*pi*1/3)
        for (int i = 0; i < a0.length; i++) {
            double a0Real = a0[i].real + a1[i].real + a2[i].real;
            double a0Imag = a0[i].imag + a1[i].imag + a2[i].imag;
            double c = omegaImag * (a2[i].imag - a1[i].imag);
            double d = omegaImag * (a1[i].real - a2[i].real);
            double e = 0.5 * (a1[i].real + a2[i].real);
            double f = 0.5 * (a1[i].imag + a2[i].imag);
            double a1Real = a0[i].real - e + c;
            double a1Imag = a0[i].imag + d - f;
            double a2Real = a0[i].real - e - c;
            double a2Imag = a0[i].imag - d - f;
            a0[i].real = a0Real * scale;
            a0[i].imag = a0Imag * scale;
            a1[i].real = a1Real * scale;
            a1[i].imag = a1Imag * scale;
            a2[i].real = a2Real * scale;
            a2[i].imag = a2Imag * scale;
        }
    }

    // The result is placed in the argument
    private static void squarePointwise(MutableComplex[] vec) {
        for (int i = 0; i < vec.length; i++)
            vec[i].square();
    }

    // Returns n-th complex roots of unity for the angles 0..pi/2, suitable
    // for a transform of length n.
    // They are used as twiddle factors and as weights for the right-angle transform.
    // n must be 1 or an even number.
    private static MutableComplex[] calculateRootsOfUnity(int n) {
        if (n == 1) {
            return new MutableComplex[]{new MutableComplex(1, 0)};
        }
        MutableComplex[] roots = new MutableComplex[n];
        roots[0] = new MutableComplex(1, 0);
        double cos = Math.cos(0.25 * Math.PI);
        double sin = Math.sin(0.25 * Math.PI);
        roots[n / 2] = new MutableComplex(cos, sin);
        for (int i = 1; i < n / 2; i++) {
            double angle = 0.5 * Math.PI * i / n;
            cos = Math.cos(angle);
            sin = Math.sin(angle);
            roots[i] = new MutableComplex(cos, sin);
            roots[n - i] = new MutableComplex(sin, cos);
        }
        return roots;
    }

    /**
     * Returns a BigInteger whose value is {@code (a * b)}.
     *
     * @param a        value a
     * @param b        value b
     * @param parallel whether to perform the computation in parallel
     * @return {@code this * val}
     * @implNote An implementation may offer better algorithmic
     * performance when {@code a == b}.
     */
    public static BigInteger multiply(BigInteger a, BigInteger b, boolean parallel) {
        if (b.signum() == 0 || a.signum() == 0) {
            return BigInteger.ZERO;
        }
        if (b == a) {
            return square(b);
        }

        int xlen = a.bitLength();
        int ylen = b.bitLength();

        if (xlen > TOOM_COOK_THRESHOLD
                && ylen > TOOM_COOK_THRESHOLD
                && (xlen > FFT_THRESHOLD || ylen > FFT_THRESHOLD)) {
            return multiplyFFT(a, b);
        }
        return FastIntegerMath.parallelMultiply(a, b, parallel);
    }

    /**
     * Multiplies two BigIntegers using a floating-point FFT.
     * <p>
     * Floating-point math is inaccurate; to ensure the output of the FFT and
     * IFFT rounds to the correct result for every input, the provably safe
     * FFT error bounds from "Rapid Multiplication Modulo The Sum And
     * Difference of Highly Composite Numbers" by Colin Percival, pg. 392
     * (<a href="https://www.daemonology.net/papers/fft.pdf">fft.pdf</a>) are used, the vector is
     * "balanced" before the FFT, and accurate twiddle factors are used.
     * <p>
     * This implementation incorporates several features compared to the
     * standard FFT algorithm
     * (<a href="https://en.wikipedia.org/wiki/Cooley%E2%80%93Tukey_FFT_algorithm">Cooley Tukey FFT algorithm</a>):
     * <ul>
     * <li>It uses a variant called right-angle convolution which weights the
     *     vector before the transform. The benefit of the right-angle
     *     convolution is that when multiplying two numbers of length n, an
     *     FFT of length n suffices whereas a regular FFT needs length 2n.
     *     This is because the right-angle convolution places half of the
     *     result in the real part and the other half in the imaginary part.
     *     See: Discrete Weighted Transforms And Large-Integer Arithmetic by
     *     Richard Crandall and Barry Fagin.
     * <li>FFTs of length 3*2^n are supported in addition to 2^n.
     * <li>Radix-4 butterflies; see
     *     https://www.nxp.com/docs/en/application-note/AN3666.pdf
     * <li>Bernstein's conjugate twiddle trick for a small speed gain at the
     *     expense of (further) reordering the output of the FFT which is not
     *     a problem because it is reordered back in the IFFT.
     * <li>Roots of unity are cached
     * </ul>
     * FFT vectors are stored as arrays of MutableComplex objects. Storing them
     * as arrays of primitive doubles would obviously be more memory efficient,
     * but in some cases below ~10^6 decimal digits, it hurts speed because
     * it requires additional copying. Ideally this would be implemented using
     * value types when they become available.
     *
     * @param a value a
     * @param b value b
     * @return a*b
     */
    static BigInteger multiplyFFT(BigInteger a, BigInteger b) {
        int signum = a.signum() * b.signum();
        int[] aMag = getMagnitude(a);
        int[] bMag = getMagnitude(b);
        int bitLen = Math.max(aMag.length, bMag.length) * 32;
        int bitsPerPoint = bitsPerFFTPoint(bitLen);
        int fftLen = (bitLen + bitsPerPoint - 1) / bitsPerPoint + 1;   // +1 for a possible carry, see toFFTVector()
        int logFFTLen = 32 - Integer.numberOfLeadingZeros(fftLen - 1);

        // Use a 2^n or 3*2^n transform, whichever is shortest
        int fftLen2 = 1 << (logFFTLen);   // rounded to 2^n
        int fftLen3 = fftLen2 * 3 / 4;   // rounded to 3*2^n
        if (fftLen < fftLen3) {
            fftLen = fftLen3;
            MutableComplex[] aVec = toFFTVector(aMag, fftLen, bitsPerPoint);
            MutableComplex[] bVec = toFFTVector(bMag, fftLen, bitsPerPoint);
            MutableComplex[][] roots2 = getRootsOfUnity2(logFFTLen - 2);   // roots for length fftLen/3 which is a power of two
            MutableComplex[] weights = getRootsOfUnity3(logFFTLen - 2);
            MutableComplex[] twiddles = getRootsOfUnity3(logFFTLen - 4);
            applyWeights(aVec, weights);
            applyWeights(bVec, weights);
            fftMixedRadix(aVec, roots2, twiddles);
            fftMixedRadix(bVec, roots2, twiddles);
            multiplyPointwise(aVec, bVec);
            ifftMixedRadix(aVec, roots2, twiddles);
            applyInverseWeights(aVec, weights);
            BigInteger c = fromFFTVector(aVec, signum, bitsPerPoint);
            return c;
        } else {
            fftLen = fftLen2;
            MutableComplex[] aVec = toFFTVector(aMag, fftLen, bitsPerPoint);
            MutableComplex[] bVec = toFFTVector(bMag, fftLen, bitsPerPoint);
            MutableComplex[][] roots = getRootsOfUnity2(logFFTLen);
            applyWeights(aVec, roots[logFFTLen]);
            applyWeights(bVec, roots[logFFTLen]);
            fft(aVec, roots);
            fft(bVec, roots);
            multiplyPointwise(aVec, bVec);
            ifft(aVec, roots);
            applyInverseWeights(aVec, roots[logFFTLen]);
            BigInteger c = fromFFTVector(aVec, signum, bitsPerPoint);
            return c;
        }
    }

    // The result is placed in a
    private static void multiplyPointwise(MutableComplex[] a, MutableComplex[] b) {
        for (int i = 0; i < a.length; i++)
            a[i].multiply(b[i]);
    }
}
