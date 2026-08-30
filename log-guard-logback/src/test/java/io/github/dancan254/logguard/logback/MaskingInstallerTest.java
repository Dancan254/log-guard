package io.github.dancan254.logguard.logback;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static io.github.dancan254.logguard.logback.LogbackFixture.Customer;
import static io.github.dancan254.logguard.logback.LogbackFixture.listAppender;
import static io.github.dancan254.logguard.logback.LogbackFixture.masker;
import static org.assertj.core.api.Assertions.assertThat;

class MaskingInstallerTest {

    private final LoggerContext context = LogbackFixture.loggerContext();
    private final MaskingInstaller installer = new MaskingInstaller(masker());

    private static List<Appender<ILoggingEvent>> appendersOf(Logger logger) {
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        for (Iterator<Appender<ILoggingEvent>> iterator = logger.iteratorForAppenders(); iterator.hasNext(); ) {
            appenders.add(iterator.next());
        }
        return appenders;
    }

    @Test
    void should_wrap_every_attached_appender_when_applied() {
        Logger logger = context.getLogger("orders");
        logger.addAppender(listAppender(context, "console"));
        logger.addAppender(listAppender(context, "file"));

        installer.install(context);

        assertThat(appendersOf(logger))
                .hasSize(2)
                .allSatisfy(appender -> assertThat(appender).isInstanceOf(MaskingAppenderWrapper.class))
                .extracting(appender -> ((MaskingAppenderWrapper) appender).getDelegate().getName())
                .containsExactlyInAnyOrder("console", "file");
    }

    @Test
    void should_not_wrap_twice_when_applied_repeatedly() {
        Logger logger = context.getLogger("orders");
        logger.addAppender(listAppender(context, "console"));

        installer.install(context);
        installer.install(context);

        assertThat(appendersOf(logger)).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(MaskingAppenderWrapper.class))
                .extracting(MaskingAppenderWrapper::getDelegate)
                .isNotInstanceOf(MaskingAppenderWrapper.class);
    }

    @Test
    void should_wrap_the_async_appender_itself_when_one_is_attached() {
        ListAppender<ILoggingEvent> console = listAppender(context, "console");
        AsyncAppender async = new AsyncAppender();
        async.setContext(context);
        async.setName("async");
        async.addAppender(console);
        async.start();
        Logger logger = context.getLogger("orders");
        logger.addAppender(async);

        installer.install(context);

        assertThat(appendersOf(logger)).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(MaskingAppenderWrapper.class))
                .extracting(MaskingAppenderWrapper::getDelegate)
                .isSameAs(async);
    }

    @Test
    void should_mask_through_the_async_boundary_when_installed() {
        ListAppender<ILoggingEvent> console = listAppender(context, "console");
        AsyncAppender async = new AsyncAppender();
        async.setContext(context);
        async.setName("async");
        async.addAppender(console);
        async.start();
        Logger logger = context.getLogger("orders");
        logger.addAppender(async);
        installer.install(context);

        logger.info("Processing customer {}", new Customer());
        async.stop();

        assertThat(console.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("Processing customer Customer(id=42, email=j****@acme.io)");
    }
}
