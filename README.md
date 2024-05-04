# FileCalli

A new home for your files.
Made to be selfhosted.
Keep the privacy of your users, as their files are encrypted. 

## Features

- [ ] Web interface (soon)
- [x] File encryption, only the owner can see the file
- [x] Webdav support with granular permissions

## Installation

Pull the docker image and run it with the following command:

```bash
docker run -d -p 8080:8080 -v /your/data/dir:/data --mount type=tmpfs,destination=/tmp,tmpfs-size=512m filecalli/filecalli
```

### Setup

Before executing the image, you need to create a configuration file `setup.yml` in the data directory. The file should look like this:

```yaml
users:
  - id: user1
    name: User 1
    pass: mypassword
    role: admin
    webdav: yes
    webdavTokens:
      - token: 123456
        path: /
```

This way the db will be created and populated with the defined users.
You can as many users as you want and you can create them later in the web interface.
Is recommended to only create the admin user and let the users setup their own accounts.
File `setup.yml` will be deleted after the first run.

The password cannot be changed because is used to encrypt the files.
The password is only know at runtime while the session is active.

## Configuration

- host: Hostname of the server. Used to generate the webdav url. Default: `http://localhost:8080`
- contextpath: Context path of the server. Default empty.
- datapath: Path to the data directory. Default: `/data` for image
- temppath: Path to the temporary directory. Default: `/tmp` for image
- salt: Salt used to encrypt the files. Default: `calli`. Recommended to use your own.

Example:

```bash
docker run -e host=http://example.com -e contextpath=/filecalli -e salt=mysecret fillcalli/filecalli
```

## Webdav

Each user has a special webdav directory that can be accessed with a token.
The token is generated by the user and can be revoked at any time.
The token is used to authenticate the user and the path is used to restrict the access to the files.
Webdav files are not encrypted with the user password but with the salt and a random key specific to the user.

## Documentation

I'll be using [my blog](https://vjdv.net/tag/filecalli) to document the development of this project.