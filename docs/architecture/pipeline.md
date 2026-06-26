# Pipeline

## Processing Flow

User Query ↓ Language ↓ Intent ↓ Policy ↓ Threat ↓ Rewrite ↓ Semantic ↓
Decision ↓ Confidence ↓ Execution Plan ↓ Execution Engine ↓ Execution
Result

Each stage is isolated and produces its own immutable Result object.

ExecutionContext transports results between stages while keeping steps
independent.

The Execution Engine dispatches every ExecutionStep to an
ExecutionOperationExecutor implementation.
