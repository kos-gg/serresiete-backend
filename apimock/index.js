const express = require('express')
const {join} = require("path");
const app = express()
const fs = require('fs');
const port = 8080
const cors = require('cors')

app.use(express.json());
app.use(cors())

app.get('/api/views/:id/data', (req, res) => {
    const viewId = req.params.id;

    if (viewId === 'wow-hardcore-view') return res.sendFile(join(__dirname, 'resources', 'wow-hc-data.json'));
    if (viewId === 'lol-view') return res.sendFile(join(__dirname, 'resources', 'lol-data.json'));
    return res.status(404).send('Not found')
});

app.get('/api/views/:id/cached-data', (req, res) => {
    const viewId = req.params.id;

    if (viewId === 'lol-view') return res.sendFile(join(__dirname, 'resources', 'lol-cached-data.json'));
    return res.status(404).send('Not found')
});

app.get('/api/views', (req, res) => {
    const filePath = join(__dirname, 'resources', 'views.json');
    const game = req.query.game;

    fs.readFile(filePath, 'utf8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Failed to read file' });

        const parsedData = JSON.parse(data);
        const { records, metadata } = parsedData;

        if (game) {
            const filteredRecords = records.filter(view => view.game === game.toUpperCase());
            return res.json({ metadata, records: filteredRecords });
        }
        return res.json(parsedData);
    });
});

app.get('/api/credentials/:userName', (req, res) => {
    const userName = req.params.userName;
    const filePath = join(__dirname, 'resources', 'user.json')

    fs.readFile(filePath, 'utf8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Failed to read file' });

        const user = JSON.parse(data);

        if (user.userName === userName) return res.json(user);

        return res.status(404).json({error: 'User not found'});

    })
})

app.listen(port, () => {
    console.log(`Mock server running at http://localhost:${port}`);
});
