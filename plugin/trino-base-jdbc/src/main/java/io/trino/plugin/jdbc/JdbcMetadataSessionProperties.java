/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.jdbc;

import com.google.common.collect.ImmutableList;
import io.trino.plugin.base.session.SessionPropertiesProvider;
import io.trino.spi.TrinoException;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.session.PropertyMetadata;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;

import static io.trino.plugin.jdbc.JdbcMetadataConfig.MAX_ALLOWED_WRITE_BATCH_SIZE;
import static io.trino.spi.StandardErrorCode.INVALID_SESSION_PROPERTY;
import static io.trino.spi.session.PropertyMetadata.booleanProperty;
import static io.trino.spi.session.PropertyMetadata.integerProperty;
import static java.lang.String.format;

public class JdbcMetadataSessionProperties
        implements SessionPropertiesProvider
{
    public static final String JOIN_PUSHDOWN_ENABLED = "join_pushdown_enabled";
    public static final String AGGREGATION_PUSHDOWN_ENABLED = "aggregation_pushdown_enabled";
    public static final String TOPN_PUSHDOWN_ENABLED = "topn_pushdown_enabled";
    public static final String DOMAIN_COMPACTION_THRESHOLD = "domain_compaction_threshold";
    public static final String WRITE_BATCH_SIZE = "write_batch_size";
    public static final String NON_TRANSACTIONAL_INSERT = "non_transactional_insert";

    private final List<PropertyMetadata<?>> properties;

    @Inject
    public JdbcMetadataSessionProperties(JdbcMetadataConfig jdbcMetadataConfig, @MaxDomainCompactionThreshold Optional<Integer> maxDomainCompactionThreshold)
    {
        validateDomainCompactionThreshold(jdbcMetadataConfig.getDomainCompactionThreshold(), maxDomainCompactionThreshold);
        properties = ImmutableList.<PropertyMetadata<?>>builder()
                .add(booleanProperty(
                        JOIN_PUSHDOWN_ENABLED,
                        "Enable join pushdown",
                        jdbcMetadataConfig.isJoinPushdownEnabled(),
                        false))
                .add(booleanProperty(
                        AGGREGATION_PUSHDOWN_ENABLED,
                        "Enable aggregation pushdown",
                        jdbcMetadataConfig.isAggregationPushdownEnabled(),
                        false))
                .add(integerProperty(
                        DOMAIN_COMPACTION_THRESHOLD,
                        "Maximum ranges to allow in a tuple domain without simplifying it",
                        jdbcMetadataConfig.getDomainCompactionThreshold(),
                        value -> validateDomainCompactionThreshold(value, maxDomainCompactionThreshold),
                        false))
                .add(booleanProperty(
                        TOPN_PUSHDOWN_ENABLED,
                        "Enable TopN pushdown",
                        jdbcMetadataConfig.isTopNPushdownEnabled(),
                        false))
                .add(integerProperty(
                        WRITE_BATCH_SIZE,
                        "Maximum number of rows to write in a single batch",
                        jdbcMetadataConfig.getWriteBatchSize(),
                        JdbcMetadataSessionProperties::validateWriteBatchSize,
                        false))
                .add(booleanProperty(
                        NON_TRANSACTIONAL_INSERT,
                        "Do not use temporary table on insert to table",
                        jdbcMetadataConfig.isNonTransactionalInsert(),
                        false))
                .build();
    }

    @Override
    public List<PropertyMetadata<?>> getSessionProperties()
    {
        return properties;
    }

    public static boolean isJoinPushdownEnabled(ConnectorSession session)
    {
        return session.getProperty(JOIN_PUSHDOWN_ENABLED, Boolean.class);
    }

    public static boolean isAggregationPushdownEnabled(ConnectorSession session)
    {
        return session.getProperty(AGGREGATION_PUSHDOWN_ENABLED, Boolean.class);
    }

    public static boolean isTopNPushdownEnabled(ConnectorSession session)
    {
        return session.getProperty(TOPN_PUSHDOWN_ENABLED, Boolean.class);
    }

    public static int getDomainCompactionThreshold(ConnectorSession session)
    {
        return session.getProperty(DOMAIN_COMPACTION_THRESHOLD, Integer.class);
    }

    public static int getWriteBatchSize(ConnectorSession session)
    {
        return session.getProperty(WRITE_BATCH_SIZE, Integer.class);
    }

    public static boolean isNonTransactionalInsert(ConnectorSession session)
    {
        return session.getProperty(NON_TRANSACTIONAL_INSERT, Boolean.class);
    }

    private static void validateDomainCompactionThreshold(int domainCompactionThreshold, Optional<Integer> maxDomainCompactionThreshold)
    {
        if (domainCompactionThreshold < 1) {
            throw new TrinoException(INVALID_SESSION_PROPERTY, format("Domain compaction threshold (%s) must be greater than 0", domainCompactionThreshold));
        }

        maxDomainCompactionThreshold.ifPresent(max -> {
            if (domainCompactionThreshold > max) {
                throw new TrinoException(INVALID_SESSION_PROPERTY, format("Domain compaction threshold (%s) cannot exceed %s", domainCompactionThreshold, max));
            }
        });
    }

    private static void validateWriteBatchSize(int maxBatchSize)
    {
        if (maxBatchSize < 1) {
            throw new TrinoException(INVALID_SESSION_PROPERTY, format("%s must be greater than 0: %s", WRITE_BATCH_SIZE, maxBatchSize));
        }
        if (maxBatchSize > MAX_ALLOWED_WRITE_BATCH_SIZE) {
            throw new TrinoException(INVALID_SESSION_PROPERTY, format("%s cannot exceed %s: %s", WRITE_BATCH_SIZE, MAX_ALLOWED_WRITE_BATCH_SIZE, maxBatchSize));
        }
    }
}
