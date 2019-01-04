import React from 'react'
import { defaultState, Provider } from './TMTAuthContext'
import PropTypes from 'prop-types'
import { TMTAuth } from '../TMTAuth'

class TMTAuthContextProvider extends React.Component {
  constructor() {
    super()
    this.state = { ...defaultState, login: this.login, logout: this.logout }
  }

  render() {
    return (
      <Provider value={this.state}>
        { this.props.children }
      </Provider>
    )
  }

  instantiateAAS = async url => {
    const { keycloak, authenticated } = await TMTAuth.authenticate(
      this.props.config,
      url,
    )
    authenticated
      .success(() => {
        const tmtAuth = TMTAuth.from(keycloak)
        console.log('**********instantiateAAS*********')
        this.setState({tmtAuth: tmtAuth, isAuthenticated: tmtAuth.isAuthenticated})
      })
      .error(() => {
        this.setState({tmtAuth: null, isAuthenticated: false})
      })
  }

  login = async () => {
    console.log('logging in')
    const url = await TMTAuth.getAASUrl()
    await this.instantiateAAS({ url: url })
  }

  logout = () => {
    this.state.tmtAuth.logout()
    this.setState({tmtAuth: null, isAuthenticated: false})
  }
}

TMTAuthContextProvider.propTypes = {
  config: PropTypes.object,
  children: PropTypes.node
}

export default TMTAuthContextProvider
