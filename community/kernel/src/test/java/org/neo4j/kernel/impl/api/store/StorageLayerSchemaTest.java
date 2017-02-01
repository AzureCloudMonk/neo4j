/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.store;

import org.junit.Test;

import java.util.Set;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.api.schema.NodePropertyDescriptor;
import org.neo4j.kernel.api.constraints.NodePropertyConstraint;
import org.neo4j.kernel.api.constraints.PropertyConstraint;
import org.neo4j.kernel.api.constraints.UniquenessConstraint;
import org.neo4j.kernel.api.schema_new.LabelSchemaDescriptor;
import org.neo4j.kernel.api.schema_new.SchemaDescriptorFactory;

import static org.junit.Assert.assertEquals;
import static org.neo4j.helpers.collection.Iterators.asSet;

public class StorageLayerSchemaTest extends StorageLayerTest
{
    @Test
    public void shouldListAllConstraints()
    {
        // Given
        createUniquenessConstraint( label1, propertyKey );
        createUniquenessConstraint( label2, propertyKey );

        // When
        Set<PropertyConstraint> constraints = asSet( disk.constraintsGetAll() );

        // Then
        int labelId1 = labelId( label1 );
        int labelId2 = labelId( label2 );
        int propKeyId = propertyKeyId( propertyKey );

        Set<?> expectedConstraints = asSet(
                new UniquenessConstraint( new NodePropertyDescriptor( labelId1, propKeyId ) ),
                new UniquenessConstraint( new NodePropertyDescriptor( labelId2, propKeyId ) ) );

        assertEquals( expectedConstraints, constraints );
    }

    @Test
    public void shouldListAllConstraintsForLabel()
    {
        // Given
        createUniquenessConstraint( label1, propertyKey );
        createUniquenessConstraint( label2, propertyKey );

        // When
        Set<NodePropertyConstraint> constraints = asSet( disk.constraintsGetForLabel( labelId( label1 ) ) );

        // Then
        Set<?> expectedConstraints = asSet( new UniquenessConstraint( descriptorFrom( label1, propertyKey ) ) );

        assertEquals( expectedConstraints, constraints );
    }

    @Test
    public void shouldListAllConstraintsForLabelAndProperty()
    {
        // Given
        createUniquenessConstraint( label1, propertyKey );
        createUniquenessConstraint( label1, otherPropertyKey );

        // When
        Set<NodePropertyConstraint> constraints = asSet(
                disk.constraintsGetForLabelAndPropertyKey( newDescriptorFrom( label1, propertyKey ) ) );

        // Then
        Set<?> expectedConstraints = asSet(
                new UniquenessConstraint( descriptorFrom( label1, propertyKey ) ) );

        assertEquals( expectedConstraints, constraints );
    }

    private void createUniquenessConstraint( Label label, String propertyKey )
    {
        try ( Transaction tx = db.beginTx() )
        {
            db.schema().constraintFor( label ).assertPropertyIsUnique( propertyKey ).create();
            tx.success();
        }
    }

    private NodePropertyDescriptor descriptorFrom( Label label, String propertyKey )
    {
        return new NodePropertyDescriptor( labelId( label ), propertyKeyId( propertyKey ) );
    }

    private LabelSchemaDescriptor newDescriptorFrom( Label label, String propertyKey )
    {
        return SchemaDescriptorFactory.forLabel( labelId( label ), propertyKeyId( propertyKey ) );
    }
}
