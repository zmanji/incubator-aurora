#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Detect Thermos tasks on disk

This module contains the TaskDetector, used to detect Thermos tasks within a given checkpoint root.

"""

import glob
import os
import re
from abc import abstractmethod

from twitter.common.lang import Interface

from apache.thermos.common.path import TaskPath


class PathDetector(Interface):
  @abstractmethod
  def get_paths(self):
    """Get a list of valid checkpoint roots."""


class FixedPathDetector(PathDetector):
  def __init__(self, path=TaskPath.DEFAULT_CHECKPOINT_ROOT):
    self._paths = [path]

  def get_paths(self):
    return self._paths[:]


class ChainedPathDetector(PathDetector):
  def __init__(self, *detectors):
    for detector in detectors:
      if not isinstance(detector, PathDetector):
        raise TypeError('Expected detector %r to be a PathDetector, got %s' % (
            detector, type(detector)))
    self._detectors = detectors

  def get_paths(self):
    def iterate():
      for detector in self._detectors:
        for path in detector.get_paths():
          yield path
    return list(set(iterate()))


class TaskDetector(object):
  """
    Helper class in front of TaskPath to detect active/finished/running tasks. Performs no
    introspection on the state of a task; merely detects based on file paths on disk.
  """
  class MatchingError(Exception): pass

  def __init__(self, path_detector=None, root=None):
    self._path_detector = path_detector

    if root:
      self._path_detector = FixedPathDetector(root)

    elif (not isinstance(path_detector, PathDetector)):
      raise TypeError('Expected path_detector %r to be a PathDetector, got %s' % (
        path_detector, type(path_detector)))

    self._pathspec = TaskPath()

  def get_task_ids(self, state=None):
    for root_dir in self._path_detector.get_paths():

      paths = glob.glob(self._pathspec.given(root=root_dir,
                                             task_id="*",
                                             state=state or '*')
                                      .getpath('task_path'))
      path_re = re.compile(self._pathspec.given(root=re.escape(root_dir),
                                                task_id="(\S+)",
                                                state='(\S+)')
                                         .getpath('task_path'))
      for path in paths:
        try:
          task_state, task_id = path_re.match(path).groups()
        except Exception:
          continue
        if state is None or task_state == state:
          yield (task_state, task_id)

  def get_process_runs(self, task_id, log_dir):
    for root_dir in self._path_detector.get_paths():
      paths = glob.glob(self._pathspec.given(root=root_dir,
                                             task_id=task_id,
                                             log_dir=log_dir,
                                             process='*',
                                             run='*')
                                      .getpath('process_logdir'))
      path_re = re.compile(self._pathspec.given(root=re.escape(root_dir),
                                                task_id=re.escape(task_id),
                                                log_dir=log_dir,
                                                process='(\S+)',
                                                run='(\d+)')
                                         .getpath('process_logdir'))
      for path in paths:
        try:
          process, run = path_re.match(path).groups()
        except Exception:
          continue
        yield process, int(run)

  def get_process_logs(self, task_id, log_dir):
    for root_dir in self._path_detector.get_paths():
      for process, run in self.get_process_runs(task_id, log_dir):
        for logtype in ('stdout', 'stderr'):
          path = (self._pathspec.with_filename(logtype).given(root=root_dir,
                                                             task_id=task_id,
                                                             log_dir=log_dir,
                                                             process=process,
                                                             run=run)
                                                       .getpath('process_logdir'))
          if os.path.exists(path):
            yield path

  def get_checkpoint(self, task_id):
    for root_dir in self._path_detector.get_paths():
      path = self._pathspec.given(root=root_dir, task_id=task_id).getpath('runner_checkpoint')
      if os.path.exists(path):
        return path

  def get_process_checkpoints(self, task_id):
    for root_dir in self._path_detector.get_paths():
      matching_paths = glob.glob(self._pathspec.given(root=root_dir,
                                                      task_id=task_id,
                                                      process='*')
                                               .getpath('process_checkpoint'))
      path_re = re.compile(self._pathspec.given(root=re.escape(root_dir),
                                                task_id=re.escape(task_id),
                                                process='(\S+)')
                                         .getpath('process_checkpoint'))
      for path in matching_paths:
        try:
          process, = path_re.match(path).groups()
        except Exception:
          continue
        yield path
