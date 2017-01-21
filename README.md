# trello board csv export

Exports cards on a trello board to output.csv

Fields [:listPos :list :name :dateLastActivity :labels :members  :shortUrl :pos]

## Usage

lein run <trello-api-key> <trello-token> <trello-board-id> <delimiter>

## Trello

You can get your trello api key here https://trello.com/app-key
Click the token link to get your token
Find the board id in the board url `https://trello.com/b/<this-is-the-id>/simply-builders`
