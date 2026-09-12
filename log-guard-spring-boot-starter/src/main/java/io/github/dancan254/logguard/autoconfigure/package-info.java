/**
 * Spring Boot auto-configuration: the {@code log-guard.*} properties, installation, and the startup
 * validator.
 *
 * <p>Installation happens before the first log line rather than after the context refreshes, since
 * anything logged earlier is out of reach — Boot's banner and its own first startup lines included.
 *
 * <p>The validator scans the application's own {@code Entity} classes and reports any that declare
 * a {@code toString()} and hold a field whose name is in the personal data taxonomy while carrying
 * no {@code Pii}. It matches annotations by name through Spring's metadata reader, so the starter
 * needs no JPA dependency of its own. Lombok's {@code ToString.Exclude} cannot silence a finding,
 * because it is source-retained and invisible at runtime; {@code Pii} with the dropping strategy is
 * the opt-out.
 */
package io.github.dancan254.logguard.autoconfigure;
