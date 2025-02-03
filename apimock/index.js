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

app.post('/api/auth', (req, res) => {
    const { authorization } = req.headers;
    if (!authorization) return res.status(401).json({ error: 'Unauthorized' });

    const credentials = Buffer.from(authorization.split(' ')[1], 'base64').toString('utf-8');
    const [userName, password] = credentials.split(':');
    const filePath = join(__dirname, 'resources', 'auth.json');

    fs.readFile(filePath, 'utf8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Failed to read file' });

        const auth = JSON.parse(data);

        if (auth.userName === userName && auth.password === password) {
            return res.json({
                accessToken: auth.accessToken,
                refreshToken: auth.refreshToken
            });
        }

        return res.status(401).json({ error: 'Invalid username or password' });
    });
});

app.delete('/api/auth', (req, res) => {
    const { authorization } = req.headers;
    if (!authorization) return res.status(401).json({ error: 'Unauthorized' });

    const token = authorization.split(' ')[1];
    const filePath = join(__dirname, 'resources', 'auth.json');


    fs.readFile(filePath, 'utf8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Failed to read file' });

        const auth = JSON.parse(data);

        if (token === auth.accessToken) return res.status(200).json({ message: 'Logout successful'})
        return res.status(401).json({ error: 'Invalid token'})
        });
})

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

app.get('/api/tasks', (req, res) => {
    const filePath = join(__dirname, 'resources', 'tasks.json');

    fs.readFile(filePath, 'utf8', (err, data) => {
        if (err) return res.status(500).json({ error: 'Failed to read file' });

        const parsedData = JSON.parse(data);

        return res.json(parsedData);
    });
});

app.post('/api/views', (req, res) => {
    const { authorization } = req.headers;
    if (!authorization) return res.status(401).json({ error: 'Unauthorized' });

    const token = authorization.split(' ')[1];
    if (token !== 'mock-access-token') return res.status(401).json({ error: 'Invalid token' });

    res.status(200).json(req.body);
})

app.get('/api/views/:id', (req, res) => {
    const viewId = req.params.id;

    if (viewId === 'cf73f057-b784-4c65-88fa-7d9ec77a732a') return res.sendFile(join(__dirname, 'resources', 'wow-hc-view.json'));
    if (viewId === '685567f7-e991-4674-baf4-086f26944bee') return res.sendFile(join(__dirname, 'resources', 'wow-view.json'));
    if (viewId === '4aec2617-403e-46dd-9456-ee2350ca5df9') return res.sendFile(join(__dirname, 'resources', 'lol-view.json'));
    return res.status(404).send('Not found')
});

app.put('/api/views/:id', (req, res) => {
    const viewId = req.params.id;

    const { authorization } = req.headers;
    if (!authorization) return res.status(401).json({ error: 'Unauthorized' });

    const token = authorization.split(' ')[1];
    if (token !== 'mock-access-token') return res.status(401).json({ error: 'Invalid token' });

    const updatedView = { ...req.body, id: viewId };

    return res.status(200).json(updatedView);
})

app.delete('/api/views/:id', (req, res) => {
    const viewId = req.params.id;
    const filePath = join(__dirname, 'resources', 'views.json')

    const { authorization } = req.headers;
    if (!authorization) return res.status(401).json({ error: 'Unauthorized' });

    const token = authorization.split(' ')[1];
    if (token !== 'mock-access-token') return res.status(401).json({ error: 'Invalid token' });

    fs.readFile(filePath, 'utf8', (err, data) => {
        if (err) throw new Error('Failed to read views data');

        const jsonData = JSON.parse(data);
        const views = jsonData.records;
        const updatedViews = views.filter(view => view.id !== viewId);

        return res.status(200).json({
            metadata: jsonData.metadata,
            records: updatedViews
        });
    });
})

app.patch('/api/views/:id', (req, res) => {
    const viewId = req.params.id;

    const { authorization } = req.headers;
    if (!authorization) return res.status(401).json({ error: 'Unauthorized' });

    const token = authorization.split(' ')[1];
    if (token !== 'mock-access-token') return res.status(401).json({ error: 'Invalid token' });

    const { published, featured } = req.body;

    const updatedView = {
        id: viewId,
        ...(published !== undefined && { published }),
        ...(featured !== undefined && { featured }),
    };

    return res.status(200).json(updatedView);
});

app.listen(port, () => {
    console.log(`Mock server running at http://localhost:${port}`);
});
