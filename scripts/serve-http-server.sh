#!/bin/bash
echo "spa-client serving on http://localhost:3100"
cd "$(dirname "$0")/../spa/spa-client"
npx http-server -p 3100 -c-1
