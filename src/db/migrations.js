import { createTable, schemaMigrations } from '@nozbe/watermelondb/Schema/migrations';

export const migrations = schemaMigrations({
  migrations: [
    {
      toVersion: 2,
      steps: [
        createTable({
          name: 'progress_photos',
          columns: [
            { name: 'file_uri', type: 'string' },
            { name: 'taken_at', type: 'number', isIndexed: true },
            { name: 'bodyweight', type: 'number', isOptional: true },
            { name: 'notes', type: 'string', isOptional: true },
            { name: 'created_at', type: 'number' },
            { name: 'updated_at', type: 'number' },
          ],
        }),
      ],
    },
  ],
});
