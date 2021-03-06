/*
 * Copyright 2018-2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.services.app;

import de.ii.xtraplatform.services.domain.TaskContext;
import it.sauronsoftware.cron4j.Task;
import it.sauronsoftware.cron4j.TaskExecutionContext;

/** @author zahnen */
public class TaskCron4j extends Task {

  private final de.ii.xtraplatform.services.domain.Task task;

  public TaskCron4j(de.ii.xtraplatform.services.domain.Task task) {
    this.task = task;
  }

  @Override
  public void execute(TaskExecutionContext taskExecutionContext) throws RuntimeException {
    final TaskContext taskContext = new TaskContextCron4j(taskExecutionContext);
    task.run(taskContext);
  }

  @Override
  public boolean canBePaused() {
    return true;
  }

  @Override
  public boolean canBeStopped() {
    return true;
  }

  @Override
  public boolean supportsStatusTracking() {
    return true;
  }

  @Override
  public boolean supportsCompletenessTracking() {
    return true;
  }
}
