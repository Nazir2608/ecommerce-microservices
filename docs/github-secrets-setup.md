# GitHub Secrets Setup Guide

To enable the CI/CD pipeline, you need to configure the following secrets in your GitHub repository:
Go to **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**.

##  Deployment Secrets (Staging/Production)

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `STAGING_HOST` | IP address or hostname of your VPS | `123.45.67.89` |
| `STAGING_USER` | SSH username (usually `ubuntu`) | `ubuntu` |
| `STAGING_SSH_KEY` | Private SSH key for the deployment user | `-----BEGIN RSA PRIVATE KEY-----...` |

##  Container Registry Secrets (if using GHCR)

The pipeline uses `GITHUB_TOKEN` by default, which is automatically provided. If you use an external registry, you might need:

| Secret Name | Description |
|-------------|-------------|
| `REGISTRY_USERNAME` | Your Docker Hub / GHCR username |
| `REGISTRY_PASSWORD` | Your personal access token (PAT) |

##  Environment Protection

1. Go to **Settings** -> **Environments**.
2. Create an environment named `production`.
3. Add **Required reviewers** if you want to approve production deployments manually.
4. Add environment-specific secrets here if they differ from the repository secrets.

##  Next Steps

Once secrets are added, push a change or a tag to trigger the pipeline:
- `git push main` -> Triggers CI and staging deployment.
- `git tag v1.0.0 && git push --tags` -> Triggers release and production deployment.
