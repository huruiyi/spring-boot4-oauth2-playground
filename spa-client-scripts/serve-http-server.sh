#!/bin/bash
echo "spa-client serving on http://localhost:3000"
cd "$(dirname "$0")/../spa-client"
npx http-server -p 3000 -c-1
