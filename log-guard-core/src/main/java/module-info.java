/**
 * The engine, with no dependency on anything. That is the point of the module and not an accident:
 * a privacy library with a transitive dependency tree is a harder sell to the team that approves it.
 *
 * <p>The type-aware layer reads private fields reflectively. An application running on the module
 * path has to {@code opens} the packages holding its annotated types, or those fields render as
 * {@code <unreadable>}; on the class path, which is where a Spring Boot fat jar puts everything,
 * there is nothing to do.
 */
module io.github.dancan254.logguard.core {

    exports io.github.dancan254.logguard;
    exports io.github.dancan254.logguard.exception;
    exports io.github.dancan254.logguard.mask;
    exports io.github.dancan254.logguard.meta;
    exports io.github.dancan254.logguard.pattern;
    exports io.github.dancan254.logguard.render;
}
