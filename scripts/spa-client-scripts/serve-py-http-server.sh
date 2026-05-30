#!/bin/bash
echo "spa-client serving on http://localhost:3100"
cd "$(dirname "$0")/../spa/spa-client"
python3 -m http.server 3100
