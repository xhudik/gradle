/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.tooling.fixture

import junit.framework.AssertionFailedError
import org.gradle.internal.os.OperatingSystem
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.StatusEvent
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor

class ProgressEvents implements ProgressListener {
    private final List<ProgressEvent> events = []
    private boolean dirty
    private final List<Operation> operations = new ArrayList<Operation>()
    private static final boolean IS_WINDOWS_OS = OperatingSystem.current().isWindows()
    boolean skipValidation

    void clear() {
        events.clear()
        operations.clear()
        dirty = false
    }

    /**
     * Asserts that the events form zero or more well-formed trees of operations.
     */
    void assertHasZeroOrMoreTrees() {
        if (dirty) {
            Set<OperationDescriptor> seen = []
            Map<OperationDescriptor, StartEvent> running = [:]
            for (ProgressEvent event : events) {
                assert event.displayName == event.toString()

                if (event instanceof StartEvent) {
                    def descriptor = event.descriptor
                    assert seen.add(descriptor)
                    assert !running.containsKey(descriptor)
                    running[descriptor] = event

                    // Display name should be mostly unique
                    if (!skipValidation && uniqueBuildOperation(descriptor)) {
                        if (descriptor.displayName in ['Configure settings', 'Configure build', 'Calculate task graph', 'Run tasks']) {
                            // Ignore this for now
                        } else {
                            def duplicateName = operations.find({ it.descriptor.displayName == descriptor.displayName })
                            if (duplicateName != null) {
                                throw new AssertionFailedError("Found duplicate operation '${duplicateName}' in events: " + events)
                            }
                        }
                    }

                    // parent should also be running
                    assert descriptor.parent == null || running.containsKey(descriptor.parent)
                    def parent = descriptor.parent == null ? null : operations.find { it.descriptor == descriptor.parent }

                    Operation operation = new Operation(parent, descriptor)
                    operations.add(operation)

                    assert descriptor.displayName == descriptor.toString()
                    assert event.displayName == "${descriptor.displayName} started" as String
                } else if (event instanceof FinishEvent) {
                    def descriptor = event.descriptor
                    def startEvent = running.remove(descriptor)
                    assert startEvent != null

                    // parent should still be running
                    assert descriptor.parent == null || running.containsKey(descriptor.parent)

                    def storedOperation = operations.find { it.descriptor == descriptor }
                    storedOperation.result = event.result

                    assert event.displayName.matches("\\Q${descriptor.displayName}\\E [\\w-]+")

                    // don't check event timestamp order on Windows OS
                    // timekeeping in CI environment on Windows is currently problematic
                    if (!IS_WINDOWS_OS) {
                        assert startEvent.eventTime <= event.eventTime
                    }

                    assert event.result.startTime == startEvent.eventTime
                    assert event.result.endTime == event.eventTime
                } else if (event instanceof StatusEvent) {
                    def descriptor = event.descriptor
                    // operation should still be running
                    assert running.containsKey(descriptor)
                    def operation = operations.find { it.descriptor == descriptor }
                    operation.statusEvents.add(event)
                }
                else {
                    throw new AssertionError("Unexpected type of progress event received: ${event.getClass()}")
                }
            }
            assert running.size() == 0: "Not all operations completed: ${running.values()}, events: ${events}"

            dirty = false
        }
    }

    // Ignore this check for TestOperationDescriptors as they are currently not unique when coming from different test tasks
    // Ignore resolve artifact operations as they are not necessarily unique atm
    boolean uniqueBuildOperation(OperationDescriptor operationDescriptor) {
        return !(operationDescriptor instanceof TestOperationDescriptor) && !operationDescriptor.displayName.startsWith("Resolve artifact ")
    }

    /**
     * Asserts that the events form exactly one well-formed trees of operations.
     */
    void assertHasSingleTree() {
        assertHasZeroOrMoreTrees()
        assert !operations.empty
        assert operations[0].descriptor.parent == null

        operations.tail().each { assert it.descriptor.parent != null }
    }

    /**
     * Asserts that the events form a typical tree of operations for a build.
     */
    void assertIsABuild() {
        assertHasSingleTree()

        def root = operations[0]
        assert root.buildOperation
        assert root.descriptor.displayName == 'Run build'
    }

    boolean isEmpty() {
        assertHasZeroOrMoreTrees()
        return events.empty
    }

    /**
     * Returns all events, in the order received.
     */
    List<ProgressEvent> getAll() {
        assertHasZeroOrMoreTrees()
        return events
    }

    /**
     * Returns all operations, in the order started.
     */
    List<Operation> getOperations() {
        assertHasZeroOrMoreTrees()
        return operations
    }

    /**
     * Returns all generic build operations, in the order started.
     */
    List<Operation> getBuildOperations() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.buildOperation } as List
    }

    /**
     * Returns all tests, in the order started.
     */
    List<Operation> getTests() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.test } as List
    }

    /**
     * Returns all tasks, in the order started.
     */
    List<Operation> getTasks() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.task } as List
    }

    /**
     * Returns all successful operations, in the order started.
     */
    List<Operation> getSuccessful() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.successful } as List
    }

    /**
     * Returns all failed operations, in the order started.
     */
    List<Operation> getFailed() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.failed } as List
    }

    /**
     * Returns the operation with the given display name. Fails when there is not exactly one such operation.
     *
     * @param displayNames candidate display names (may be different depending on the Gradle version under test)
     */
    Operation operation(String... displayNames) {
        assertHasZeroOrMoreTrees()
        def operation = operations.find { it.descriptor.displayName in displayNames }
        if (operation == null) {
            throw new AssertionFailedError("No operation with display name '${displayNames[0]}' found in: $operations")
        }
        return operation
    }

    /**
     * Returns the operation with the given display name. Fails when there is not exactly one such operation.
     *
     * @param displayNames candidate display names (may be different depending on the Gradle version under test)
     */
    Operation operation(Operation parent, String... displayNames) {
        assertHasZeroOrMoreTrees()
        def operation = operations.find { it.parent == parent && it.descriptor.displayName in displayNames }
        if (operation == null) {
            throw new AssertionFailedError("No operation with display name '${displayNames[0]}' and parent '$parent' found in: $operations")
        }
        return operation
    }

    @Override
    void statusChanged(ProgressEvent event) {
        dirty = true
        operations.clear()
        events << event
    }

    static class Operation {
        final OperationDescriptor descriptor
        final Operation parent
        final List<Operation> children = []
        final List<StatusEvent> statusEvents = []
        OperationResult result

        private Operation(Operation parent, OperationDescriptor descriptor) {
            this.descriptor = descriptor
            this.parent = parent
            if (parent != null) {
                parent.children.add(this)
            }
        }

        @Override
        String toString() {
            return descriptor.displayName
        }

        boolean isTest() {
            return descriptor instanceof TestOperationDescriptor
        }

        boolean isTask() {
            return descriptor instanceof TaskOperationDescriptor
        }

        boolean isBuildOperation() {
            return !test && !task
        }

        boolean isSuccessful() {
            return result instanceof SuccessResult
        }

        boolean isFailed() {
            return result instanceof FailureResult
        }

        List<Failure> getFailures() {
            assert result instanceof FailureResult
            return result.failures
        }

        Operation child(String displayName) {
            def child = children.find { it.descriptor.displayName == displayName }
            if (child == null) {
                throw new AssertionFailedError("No operation with display name '$displayName' found in children of '$descriptor.displayName': $children")
            }
            return child
        }

        Operation descendant(String displayName) {
            def found = [] as List<Operation>
            def descendantsText = ''
            def recurse
            recurse = { List<Operation> children, int level = 0 ->
                children.each { child ->
                    if (child.descriptor.displayName == displayName) {
                        found += child
                    }
                    descendantsText += "\t${' ' * level}${child.descriptor.displayName}\n"
                    recurse child.children, level + 1
                }
            }
            recurse children
            if (found.size() == 1) {
                return found[0]
            }
            if (found.empty) {
                throw new AssertionFailedError("No operation with display name '$displayName' found in descendants of '$descriptor.displayName':\n$descendantsText")
            }
            throw new AssertionFailedError("More than one operation with display name '$displayName' found in descendants of '$descriptor.displayName':\n$descendantsText")
        }
    }
}
