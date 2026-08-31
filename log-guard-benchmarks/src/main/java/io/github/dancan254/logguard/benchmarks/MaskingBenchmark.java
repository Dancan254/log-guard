package io.github.dancan254.logguard.benchmarks;

import io.github.dancan254.logguard.BuiltInPattern;
import io.github.dancan254.logguard.FailureMode;
import io.github.dancan254.logguard.LogGuardMasker;
import io.github.dancan254.logguard.MaskStrategy;
import io.github.dancan254.logguard.MaskingConfig;
import io.github.dancan254.logguard.NestingConfig;
import io.github.dancan254.logguard.Pii;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class MaskingBenchmark {

    public static class Customer {
        Long id = 42L;
        @Pii(strategy = MaskStrategy.HASH)
        String email = "jane.wanjiru@acme.io";
        @Pii(strategy = MaskStrategy.PARTIAL)
        String phoneNumber = "+254712345891";
        @Pii
        String nationalId = "31234567";
        @Pii
        LocalDate dateOfBirth = LocalDate.of(1994, 3, 11);
        String city = "Nairobi";
    }

    /** No annotation anywhere: the shape of the overwhelming majority of logged arguments. */
    public static class Plain {
        Long id = 42L;
        String city = "Nairobi";

        @Override
        public String toString() {
            return "Plain(id=" + id + ", city=" + city + ")";
        }
    }

    public static class WideEntity {
        @Pii
        String field00 = "value";
        String field01 = "value";
        String field02 = "value";
        String field03 = "value";
        String field04 = "value";
        String field05 = "value";
        String field06 = "value";
        String field07 = "value";
        String field08 = "value";
        String field09 = "value";
        String field10 = "value";
        String field11 = "value";
        String field12 = "value";
        String field13 = "value";
        String field14 = "value";
        String field15 = "value";
        String field16 = "value";
        String field17 = "value";
        String field18 = "value";
        String field19 = "value";
    }

    private LogGuardMasker masker;
    private Customer customer;
    private Plain plain;
    private WideEntity wide;
    private List<Customer> customers;

    private static final String CLEAN_LINE =
            "Order ORD-9 accepted for warehouse Nairobi in 41 ms";
    private static final String LINE_WITH_PII =
            "binding parameter (3:VARCHAR) <- [jane.wanjiru@acme.io]";

    @Setup
    public void setUp() {
        masker = new LogGuardMasker(new MaskingConfig(true, true,
                List.of(BuiltInPattern.EMAIL, BuiltInPattern.PHONE_E164, BuiltInPattern.CREDIT_CARD),
                List.of(), "pepper", Set.of(), NestingConfig.DEFAULT, FailureMode.PLACEHOLDER,
                MaskingConfig.DEFAULT_MAX_MESSAGE_LENGTH));
        customer = new Customer();
        plain = new Plain();
        wide = new WideEntity();
        customers = List.of(new Customer(), new Customer(), new Customer());
    }

    /** The number that decides whether this library stays switched on. */
    @Benchmark
    public Object noPiiFastPath() {
        return masker.maskArgument(plain);
    }

    @Benchmark
    public String cleanMessageThroughPatterns() {
        return masker.maskMessage(CLEAN_LINE);
    }

    @Benchmark
    public String messageWithPiiThroughPatterns() {
        return masker.maskMessage(LINE_WITH_PII);
    }

    @Benchmark
    public Object typeAwareRender() {
        return masker.maskArgument(customer);
    }

    @Benchmark
    public Object nestedRender() {
        return masker.maskArgument(customers);
    }

    @Benchmark
    public Object wideEntityRender() {
        return masker.maskArgument(wide);
    }

    /** Both layers, the way an appender runs them: render the argument, then scan the message. */
    @Benchmark
    public String fullPipeline() {
        return masker.maskMessage("Registered customer " + masker.maskArgument(customer));
    }
}
