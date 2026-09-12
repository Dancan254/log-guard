/**
 * The pattern layer: regular expressions over the already-formatted message.
 *
 * <p>This is the half that covers code you do not own — a Hibernate bind-parameter trace, a driver
 * quoting the offending value back in an exception message — where there is no annotation to read.
 * It cannot see unstructured text, so it complements the type-aware layer rather than replacing it.
 *
 * <p>Two safeguards matter here. A flat ASCII prefilter runs first, so a line containing no trigger
 * character never reaches a regex at all. And a length cap bounds what is scanned: past the cap the
 * head is masked and the tail is truncated at a token boundary, because skipping the scan on long
 * input would be a leak anyone could trigger by padding a field. It fails closed.
 */
package io.github.dancan254.logguard.pattern;
