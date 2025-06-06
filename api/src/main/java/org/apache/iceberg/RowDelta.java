/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg;

import org.apache.iceberg.expressions.Expression;

/**
 * API for encoding row-level changes to a table.
 *
 * <p>This API accumulates data and delete file changes, produces a new {@link Snapshot} of the
 * table, and commits that snapshot as the current.
 *
 * <p>When committing, these changes will be applied to the latest table snapshot. Commit conflicts
 * will be resolved by applying the changes to the new latest snapshot and reattempting the commit.
 */
public interface RowDelta extends SnapshotUpdate<RowDelta> {
  /**
   * Add a {@link DataFile} to the table.
   *
   * @param inserts a data file of rows to insert
   * @return this for method chaining
   */
  RowDelta addRows(DataFile inserts);

  /**
   * Add a {@link DeleteFile} to the table.
   *
   * @param deletes a delete file of rows to delete
   * @return this for method chaining
   */
  RowDelta addDeletes(DeleteFile deletes);

  /**
   * Remove a {@link DataFile} from the table.
   *
   * @param file a data file
   * @return this for method chaining
   */
  default RowDelta removeRows(DataFile file) {
    throw new UnsupportedOperationException(
        getClass().getName() + " does not implement deleteFile");
  }

  /**
   * Removes a rewritten {@link DeleteFile} from the table.
   *
   * @param deletes a delete file that can be removed from the table
   * @return this for method chaining
   */
  default RowDelta removeDeletes(DeleteFile deletes) {
    throw new UnsupportedOperationException(
        getClass().getName() + " does not implement removeDeletes");
  }

  /**
   * Set the snapshot ID used in any reads for this operation.
   *
   * <p>Validations will check changes after this snapshot ID. If the from snapshot is not set, all
   * ancestor snapshots through the table's initial snapshot are validated.
   *
   * @param snapshotId a snapshot ID
   * @return this for method chaining
   */
  RowDelta validateFromSnapshot(long snapshotId);

  /**
   * Enables or disables case sensitive expression binding for validations that accept expressions.
   *
   * @param caseSensitive whether expression binding should be case sensitive
   * @return this for method chaining
   */
  RowDelta caseSensitive(boolean caseSensitive);

  /**
   * Add data file paths that must not be removed by conflicting commits for this RowDelta to
   * succeed.
   *
   * <p>If any path has been removed by a conflicting commit in the table since the snapshot passed
   * to {@link #validateFromSnapshot(long)}, the operation will fail with a {@link
   * org.apache.iceberg.exceptions.ValidationException}.
   *
   * <p>By default, this validation checks only rewrite and overwrite commits. To apply validation
   * to delete commits, call {@link #validateDeletedFiles()}.
   *
   * @param referencedFiles file paths that are referenced by a position delete file
   * @return this for method chaining
   */
  RowDelta validateDataFilesExist(Iterable<? extends CharSequence> referencedFiles);

  /**
   * Enable validation that referenced data files passed to {@link
   * #validateDataFilesExist(Iterable)} have not been removed by a delete operation.
   *
   * <p>If a data file has a row deleted using a position delete file, rewriting or overwriting the
   * data file concurrently would un-delete the row. Deleting the data file is normally allowed, but
   * a delete may be part of a transaction that reads and re-appends a row. This method is used to
   * validate deletes for the transaction case.
   *
   * @return this for method chaining
   */
  RowDelta validateDeletedFiles();

  /**
   * Sets a conflict detection filter used to validate concurrently added data and delete files.
   *
   * <p>If not called, a true literal will be used as the conflict detection filter.
   *
   * @param conflictDetectionFilter an expression on rows in the table
   * @return this for method chaining
   */
  RowDelta conflictDetectionFilter(Expression conflictDetectionFilter);

  /**
   * Enables validation that data files added concurrently do not conflict with this commit's
   * operation.
   *
   * <p>This method should be called when the table is queried to determine which files to
   * delete/append. If a concurrent operation commits a new file after the data was read and that
   * file might contain rows matching the specified conflict detection filter, this operation will
   * detect this during retries and fail.
   *
   * <p>Calling this method is required to maintain serializable isolation for update/delete
   * operations. Otherwise, the isolation level will be snapshot isolation.
   *
   * <p>Validation uses the conflict detection filter passed to {@link
   * #conflictDetectionFilter(Expression)} and applies to operations that happened after the
   * snapshot passed to {@link #validateFromSnapshot(long)}.
   *
   * @return this for method chaining
   */
  RowDelta validateNoConflictingDataFiles();

  /**
   * Enables validation that delete files added concurrently do not conflict with this commit's
   * operation.
   *
   * <p>This method must be called when the table is queried to produce a row delta for UPDATE and
   * MERGE operations independently of the isolation level. Calling this method isn't required for
   * DELETE operations as it is OK to delete a record that is also deleted concurrently.
   *
   * <p>Validation uses the conflict detection filter passed to {@link
   * #conflictDetectionFilter(Expression)} and applies to operations that happened after the
   * snapshot passed to {@link #validateFromSnapshot(long)}.
   *
   * @return this for method chaining
   */
  RowDelta validateNoConflictingDeleteFiles();
}
