import React from 'react'
import Enzyme, { mount } from 'enzyme'
import Adapter from 'enzyme-adapter-react-16'

describe('<ResourceRole />', () => {
  Enzyme.configure({ adapter: new Adapter() })

  beforeEach(() => {
    jest.resetModules()
  })

  it('should render children elements if authentication is true and with valid Resource role', () => {
    const getResourceRoleWithMockContext = () => {
      const mockContext = {
        tmtAuth: {
          hasResourceRole: jest.fn().mockImplementation(() => { return true })
        },
        isAuthenticated: true,
        login: () => true,
        logout: () => true
      }
      jest.mock('../../../components/context/TMTAuthContext', () => {
        return ({
          Consumer: jest.fn().mockImplementation((props) => {
            return (
              props.children(mockContext)
            )
          })
        })
      }
      )
      return require('../../../components/authorization/ResourceRole').default
    }

    const ResourceRoleComponent = getResourceRoleWithMockContext()

    const props = {
      children: <div className='resource-role'>Authentication successful</div>,
      error: <div className='error'>Authentication unsuccessful</div>,
      resourceRole: 'test-Resource-role',
      resource: 'test-resource'
    }

    const wrapper = mount(<ResourceRoleComponent {...props} />)

    expect(wrapper.find('div.resource-role').length).toBe(1)
    expect(wrapper.find('div.error').length).toBe(0)
  })

  it('should not render children elements if authentication is true but invalid Resource role', () => {
    const getResourceRoleWithMockContext = () => {
      const mockContext = {
        tmtAuth: {
          hasResourceRole: jest.fn().mockImplementation(() => { return false })
        },
        isAuthenticated: true,
        login: () => true,
        logout: () => true
      }
      jest.mock('../../../components/context/TMTAuthContext', () => {
        return ({
          Consumer: jest.fn().mockImplementation((props) => {
            return (
              props.children(mockContext)
            )
          })
        })
      }
      )
      return require('../../../components/authorization/ResourceRole').default
    }

    const ResourceRoleComponent = getResourceRoleWithMockContext()

    const props = {
      children: <div className='resource-role'>Authentication successful</div>,
      error: <div className='error'>Authentication unsuccessful</div>,
      resourceRole: 'invalid-Resource-role',
      resource: 'invalid-resource'
    }

    const wrapper = mount(<ResourceRoleComponent {...props} />)

    expect(wrapper.find('div.resource-role').length).toBe(0)
    expect(wrapper.find('div.error').length).toBe(1)
  })

  it('should not render children elements if authentication is false ', () => {
    const getResourceRoleWithMockContext = () => {
      const mockContext = {
        tmtAuth: {
          hasResourceRole: jest.fn().mockImplementation(() => { return true })
        },
        isAuthenticated: false,
        login: () => true,
        logout: () => true
      }
      jest.mock('../../../components/context/TMTAuthContext', () => {
        return ({
          Consumer: jest.fn().mockImplementation((props) => {
            return (
              props.children(mockContext)
            )
          })
        })
      }
      )
      return require('../../../components/authorization/ResourceRole').default
    }

    const ResourceRoleComponent = getResourceRoleWithMockContext()

    const props = {
      children: <div className='Resource-role'>Authentication successful</div>,
      error: <div className='error'>Authentication unsuccessful</div>,
      resourceRole: 'invalid-Resource-role',
      resource: 'invalid-resource'
    }

    const wrapper = mount(<ResourceRoleComponent {...props} />)

    expect(wrapper.find('div.Resource-role').length).toBe(0)
    expect(wrapper.find('div.error').length).toBe(1)
  })
})
