# QwenBridge TypeScript SDK Example

Example usage of the official QwenBridge TypeScript SDK.

## Prerequisites

- Node.js 20+
- A running QwenBridge server

## Install and build

From this directory:

    npm install
    npm run build

## Run the synchronous analysis example

    npm run sync

## Run the typed streaming example

    npm run stream

By default, the examples use:

    http://localhost:8080

Override the server URL when needed:

    QWENBRIDGE_BASE_URL=http://localhost:8080 npm run sync
    QWENBRIDGE_BASE_URL=http://localhost:8080 npm run stream
