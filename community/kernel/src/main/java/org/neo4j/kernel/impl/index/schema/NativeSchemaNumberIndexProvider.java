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
package org.neo4j.kernel.impl.index.schema;

import java.io.File;
import java.io.IOException;

import org.neo4j.index.internal.gbptree.GBPTree;
import org.neo4j.index.internal.gbptree.Layout;
import org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.api.index.IndexAccessor;
import org.neo4j.kernel.api.index.IndexPopulator;
import org.neo4j.kernel.api.index.InternalIndexState;
import org.neo4j.kernel.api.index.SchemaIndexProvider;
import org.neo4j.kernel.api.schema.index.IndexDescriptor;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.kernel.impl.storemigration.StoreMigrationParticipant;

public class NativeSchemaNumberIndexProvider extends SchemaIndexProvider
{
    private static final String KEY = "native";
    private static final Descriptor NATIVE_PROVIDER_DESCRIPTOR = new Descriptor( KEY, "1.0" );
    private final PageCache pageCache;
    private final File nativeSchemaIndexBaseDir;
    private final RecoveryCleanupWorkCollector recoveryCleanupWorkCollector;
    private final boolean readOnly;

    NativeSchemaNumberIndexProvider( PageCache pageCache, File nativeSchemaIndexBaseDir,
            RecoveryCleanupWorkCollector recoveryCleanupWorkCollector, boolean readOnly )
    {
        super( NATIVE_PROVIDER_DESCRIPTOR, 0 );
        this.pageCache = pageCache;
        this.nativeSchemaIndexBaseDir = getSchemaIndexStoreDirectory( nativeSchemaIndexBaseDir );
        this.recoveryCleanupWorkCollector = recoveryCleanupWorkCollector;
        this.readOnly = readOnly;
    }

    @Override
    public IndexPopulator getPopulator( long indexId, IndexDescriptor descriptor, IndexSamplingConfig samplingConfig )
    {
        if ( readOnly )
        {
            throw new UnsupportedOperationException( "Can't create populator for read only index" );
        }

        File storeFile = nativeIndexFileFromIndexId( indexId );
        switch ( descriptor.type() )
        {
        case GENERAL:
            return new NativeNonUniqueSchemaNumberIndexPopulator<>( pageCache, storeFile, new NonUniqueNumberLayout(), samplingConfig );
        case UNIQUE:
            return new NativeUniqueSchemaNumberIndexPopulator<>( pageCache, storeFile, new UniqueNumberLayout() );
        default:
            throw new UnsupportedOperationException( "Can not create index populator of type " + descriptor.type() );
        }
    }

    @Override
    public IndexAccessor getOnlineAccessor( long indexId, IndexDescriptor descriptor, IndexSamplingConfig samplingConfig )
            throws IOException
    {
        File storeFile = nativeIndexFileFromIndexId( indexId );
        NumberLayout layout;
        switch ( descriptor.type() )
        {
        case GENERAL:
            layout = new NonUniqueNumberLayout();
            break;
        case UNIQUE:
            layout = new UniqueNumberLayout();
            break;
        default:
            throw new UnsupportedOperationException( "Can not create index accessor of type " + descriptor.type() );
        }
        return new NativeSchemaNumberIndexAccessor<>( pageCache, storeFile, layout, recoveryCleanupWorkCollector );
    }

    @Override
    public String getPopulationFailure( long indexId ) throws IllegalStateException
    {
        try
        {
            String failureMessage = readPopulationFailure( indexId );
            if ( failureMessage == null )
            {
                throw new IllegalStateException( "Index " + indexId + " isn't failed" );
            }
            return failureMessage;
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    private String readPopulationFailure( long indexId ) throws IOException
    {
        NativeSchemaIndexHeaderReader headerReader = new NativeSchemaIndexHeaderReader();
        GBPTree.readHeader( pageCache, nativeIndexFileFromIndexId( indexId ), new ReadOnlyMetaNumberLayout(), headerReader );
        return headerReader.failureMessage;
    }

    @Override
    public InternalIndexState getInitialState( long indexId, IndexDescriptor descriptor )
    {
        return null;
    }

    @Override
    public StoreMigrationParticipant storeMigrationParticipant( FileSystemAbstraction fs, PageCache pageCache )
    {
        return null;
    }

    private File nativeIndexFileFromIndexId( long indexId )
    {
        return new File( nativeSchemaIndexBaseDir, Long.toString( indexId ) );
    }

    private class ReadOnlyMetaNumberLayout extends Layout.ReadOnlyMetaLayout
    {
        @Override
        public boolean compatibleWith( long layoutIdentifier, int majorVersion, int minorVersion )
        {
            return (layoutIdentifier == UniqueNumberLayout.IDENTIFIER &&
                    majorVersion == UniqueNumberLayout.MAJOR_VERSION &&
                    minorVersion == UniqueNumberLayout.MINOR_VERSION) ||
                    (layoutIdentifier == NonUniqueNumberLayout.IDENTIFIER &&
                            majorVersion == NonUniqueNumberLayout.MAJOR_VERSION &&
                            minorVersion == NonUniqueNumberLayout.MINOR_VERSION);
        }
    }
}
