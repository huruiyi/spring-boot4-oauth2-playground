#!/bin/bash
echo "spa-client serving on http://localhost:3000"
cd "$(dirname "$0")/../spa-client"
python3 -m http.server 3000
